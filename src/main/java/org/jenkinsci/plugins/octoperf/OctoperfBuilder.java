package org.jenkinsci.plugins.octoperf;

import com.google.common.collect.ImmutableList;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import org.jenkinsci.plugins.octoperf.conditions.TestStopCondition;
import org.joda.time.format.DateTimeFormatter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static com.google.common.base.Strings.nullToEmpty;
import static hudson.tasks.BuildStepMonitor.BUILD;
import static java.util.Optional.ofNullable;
import static org.jenkinsci.plugins.octoperf.credentials.CredentialsService.CREDENTIALS_SERVICE;
import static org.joda.time.format.DateTimeFormat.forPattern;

@Getter
@Setter
public class OctoperfBuilder extends Builder implements SimpleBuildStep {
  private static final DateTimeFormatter DATE_FORMAT = forPattern("HH:mm:ss");
  public static final long TEN_SECS = 10_000L;

  private String credentialsId = "";
  private String workspaceId = "";
  private String projectId = "";
  private String scenarioId = "";
  private String serverUrl = "";
  private String testName = "";
  private List<? extends TestStopCondition> stopConditions = new ArrayList<>();

  @DataBoundConstructor
  public OctoperfBuilder(
      final String credentialsId,
      final String workspaceId,
      final String projectId,
      final String scenarioId,
      final String testName,
      final List<? extends TestStopCondition> stopConditions) {
    super();
    setCredentialsId(credentialsId);
    setWorkspaceId(workspaceId);
    setProjectId(projectId);
    setScenarioId(scenarioId);
    setStopConditions(stopConditions);
    setTestName(testName);
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BUILD;
  }

  public List<? extends TestStopCondition> getStopConditions() {
    return stopConditions;
  }

  @DataBoundSetter
  public void setWorkspaceId(final String workspaceId) {
    this.workspaceId = nullToEmpty(workspaceId);
  }

  @DataBoundSetter
  public void setProjectId(final String projectId) {
    this.projectId = nullToEmpty(projectId);
  }

  @DataBoundSetter
  public void setScenarioId(final String scenarioId) {
    this.scenarioId = nullToEmpty(scenarioId);
  }

  @DataBoundSetter
  public void setCredentialsId(final String credentialsId) {
    this.credentialsId = nullToEmpty(credentialsId);
  }

  @DataBoundSetter
  public void setStopConditions(final List<? extends TestStopCondition> stopConditions) {
    this.stopConditions = ofNullable(stopConditions).orElse(new ArrayList<>());
  }

  @DataBoundSetter
  public void setTestName(final String testName) {
    this.testName = nullToEmpty(testName);
  }

  @Override
  public void perform(
    @Nonnull final Run<?, ?> run,
    @Nonnull final FilePath workspace,
    @Nonnull final Launcher launcher,
    @Nonnull final TaskListener listener) {
    perform(run, workspace, listener, new EnvVars());
  }

  public void perform(
    @Nonnull final Run<?, ?> run,
    @Nonnull final FilePath workspace,
    @Nonnull final TaskListener listener,
    @Nonnull final EnvVars vars) {
    final String serverUrlConfig = OctoperfBuilderDescriptor.getDescriptor().getOctoperfURL();

    final Optional<OctoperfCredential> credentials = CREDENTIALS_SERVICE.find(credentialsId, run.getParent());

    final Callable<Result> build = new OctoPerfBuild(
      listener.getLogger(),
      credentials.map(OctoperfCredential::getUsername).orElse(""),
      credentials.map(OctoperfCredential::getPassword).orElse(null),
      scenarioId,
      ofNullable(testName),
      ImmutableList.copyOf(stopConditions),
      workspace,
      serverUrlConfig != null ? serverUrlConfig : serverUrl,
      vars
    );

    Result result;
    try {
      result = build.call();
    } catch (final Exception e) {
      result = Result.FAILURE;
      listener.fatalError("Error while running test: " + e);
    }

    run.setResult(result);
  }
}
