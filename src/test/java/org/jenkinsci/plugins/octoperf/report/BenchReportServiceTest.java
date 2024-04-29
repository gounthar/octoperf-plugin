package org.jenkinsci.plugins.octoperf.report;

import com.google.common.testing.NullPointerTester;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static org.jenkinsci.plugins.octoperf.constants.Constants.DEFAULT_API_URL;
import static org.jenkinsci.plugins.octoperf.report.BenchReportService.BENCH_REPORTS;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class BenchReportServiceTest {
  
  private static final BenchReport REPORT = BenchReportTest.newInstance();
  
  @Before
  public void before() {
  }
  
  @Test
  public void shouldPassNPETester() {
    new NullPointerTester().testConstructors(RestBenchReportService.class, PACKAGE);
  }
  
  @Test
  public void shouldGetBenchReportsSaas() {
    final String reportUrl = BENCH_REPORTS.getReportUrl(DEFAULT_API_URL, "workspaceId", REPORT.getProjectId(), REPORT.getId());
    assertEquals("https://api.octoperf.com/ui/workspace/workspaceId/project/projectId/analysis/report/id", reportUrl);
  }

  @Test
  public void shouldGetBenchReportsEnterprise() {
    final String reportUrl = BENCH_REPORTS.getReportUrl("http://localhost:8090", "workspaceId", REPORT.getProjectId(), REPORT.getId());
    assertEquals("http://localhost:8090/ui/workspace/workspaceId/project/projectId/analysis/report/id", reportUrl);
  }
}
