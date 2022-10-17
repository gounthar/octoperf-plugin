package org.jenkinsci.plugins.octoperf;

import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.octoperf.client.RestApiFactory;
import org.jenkinsci.plugins.octoperf.client.RestClientAuthenticator;
import org.jenkinsci.plugins.octoperf.conditions.StopConditionDescriptor;
import org.jenkinsci.plugins.octoperf.project.Project;
import org.jenkinsci.plugins.octoperf.scenario.Scenario;
import org.jenkinsci.plugins.octoperf.workspace.Workspace;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.*;

import static java.util.Optional.ofNullable;
import static org.jenkinsci.plugins.octoperf.client.RestClientService.CLIENTS;
import static org.jenkinsci.plugins.octoperf.constants.Constants.DEFAULT_API_URL;
import static org.jenkinsci.plugins.octoperf.credentials.CredentialsService.CREDENTIALS_SERVICE;
import static org.jenkinsci.plugins.octoperf.project.ProjectService.PROJECTS;
import static org.jenkinsci.plugins.octoperf.scenario.ScenarioService.SCENARIOS;
import static org.jenkinsci.plugins.octoperf.workspace.WorkspaceService.WORKSPACES;
import static org.kohsuke.stapler.Stapler.getCurrentRequest;

@Extension
@Getter
@Setter
public class OctoperfBuilderDescriptor extends BuildStepDescriptor<Builder> {
  private static final String ARROW = " => ";
  /** Used for default options in dropdowns. */
  protected static final String NONE_ID = "noneId";
  /** Used for default options in dropdowns. */
  protected static final String NONE_DISPLAY_TEXT = "None";

  private String octoperfURL = DEFAULT_API_URL;
  private String name = "OctoPerf Account";

  public OctoperfBuilderDescriptor() {
    super(OctoperfBuilder.class);
    load();
  }

  static OctoperfBuilderDescriptor getDescriptor() {
    return (OctoperfBuilderDescriptor) Jenkins.get().getDescriptor(OctoperfBuilder.class);
  }

  @Override
  public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
    return true;
  }

  @Override
  public String getDisplayName() {
    return "OctoPerf";
  }

  public ListBoxModel doFillCredentialsIdItems(
    @QueryParameter("credentialsId") final String credentialsId,
    final Object scope) {
    final ListBoxModel items = new ListBoxModel();
    final Set<String> ids = new LinkedHashSet<>();

    for (final OctoperfCredential c : CREDENTIALS_SERVICE.getCredentials(scope, getItem())) {
      final String id = c.getId();
      if (!ids.contains(id)) {
        final Option option =
          new Option(c.getUsername(), id, Objects.equals(id, credentialsId));
        ids.add(id);
        items.add(option);
      }
    }

    return getOptions(items);
  }

  public ListBoxModel doFillWorkspaceIdItems(
    @QueryParameter("credentialsId") final String credentialsId,
    @QueryParameter("workspaceId") final String workspaceId) {
    val credentials = getCredential(credentialsId);
    final ListBoxModel items = new ListBoxModel();
    if (credentials.isPresent()) {
      final RestApiFactory factory = getRestApiFactory(credentials.get());

      try {
        for (final Workspace workspace : WORKSPACES.getWorkspaces(factory)) {
          final String id = workspace.getId();
          final Option option =
            new Option(workspace.getName(), id, Objects.equals(id, workspaceId));
          items.add(option);
        }
      } catch (final IOException e) {
        items.add("Failed to connect to OctoPerf, please check your credentials. "+e);
        e.printStackTrace();
      }
    }

    return getOptions(items);
  }

  public ListBoxModel doFillProjectIdItems(
    @QueryParameter("credentialsId") final String credentialsId,
    @QueryParameter("workspaceId") final String workspaceId,
    @QueryParameter("projectId") final String projectId) {
    val credentials = getCredential(credentialsId);
    final ListBoxModel items = new ListBoxModel();
    if (credentials.isPresent() && isDefined(workspaceId)) {
      final RestApiFactory factory = getRestApiFactory(credentials.get());

      try {
        for (final Project project : PROJECTS.getProjects(factory, workspaceId)) {
          final String id = project.getId();
          final Option option =
            new Option(project.getName(), id, Objects.equals(id, projectId));
          items.add(option);
        }
      } catch (final IOException e) {
        items.add("Could not list projects. "+e);
        e.printStackTrace();
      }
    }

    return getOptions(items);
  }

  public ListBoxModel doFillScenarioIdItems(
    @QueryParameter final String credentialsId,
    @QueryParameter final String projectId,
    @QueryParameter final String scenarioId) {
    val credentials = getCredential(credentialsId);

    final ListBoxModel items = new ListBoxModel();
    if (credentials.isPresent() && isDefined(projectId)) {
      final RestApiFactory apiFactory = getRestApiFactory(credentials.get());

      try {
        final List<Scenario> scenariosByProject = SCENARIOS
          .getScenariosByProject(apiFactory, projectId);
        for (final Scenario s : scenariosByProject) {
          final String id = s.getId();
          items.add(new Option(s.getName(), id, Objects.equals(id, scenarioId)));
        }
      } catch (final IOException e) {
        items.add("Could not list scenarios. "+e);
        e.printStackTrace();
      }
    }

    return getOptions(items);
  }

  private static boolean isDefined(final String id) {
    return ofNullable(id)
      .map(Strings::emptyToNull)
      .filter(s -> !Objects.equals(s, NONE_ID))
      .isPresent();
  }

  private static ListBoxModel getOptions(final ListBoxModel items) {
    if (items.isEmpty()) {
      items.add(NONE_DISPLAY_TEXT, NONE_ID);
    }
    return items;
  }


  private static Optional<OctoperfCredential> getCredential(final String credentialsId) {
    final Item item = getItem();

    if (credentialsId.isEmpty()) {
      return CREDENTIALS_SERVICE.findFirst(item);
    }
    return CREDENTIALS_SERVICE.find(credentialsId, item);
  }

  private static Item getItem() {
    return getCurrentRequest().findAncestorObject(Item.class);
  }

  private RestApiFactory getRestApiFactory(final OctoperfCredential credentials) {
    final Pair<RestApiFactory, RestClientAuthenticator> pair = CLIENTS.create(octoperfURL, System.out);
    final RestApiFactory factory = pair.getLeft();

    final String username = credentials.getUsername();
    final String password = credentials.getPassword().getPlainText();
    final RestClientAuthenticator authenticator = pair.getRight();
    authenticator.onUsernameAndPassword(username, password);

    return factory;
  }

  @Override
  public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
    final String octoperfUrl = formData.optString("octoperfURL");
    this.octoperfURL = octoperfUrl.isEmpty() ? DEFAULT_API_URL : octoperfUrl;
    save();
    return true;
  }

  public List<StopConditionDescriptor> getStopConditionDescriptors() {
    return StopConditionDescriptor.all();
  }
}

