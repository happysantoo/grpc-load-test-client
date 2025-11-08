package net.vajraedge.perftest.service

import net.vajraedge.perftest.concurrency.LoadTestMode
import net.vajraedge.perftest.concurrency.RampStrategyType
import net.vajraedge.perftest.dto.TestConfigRequest
import net.vajraedge.perftest.dto.TestStatusResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

/**
 * Additional tests to improve coverage of TestExecutionService
 */
@SpringBootTest
class TestExecutionServiceAdditionalSpec extends Specification {

    @Autowired
    TestExecutionService service

    def cleanup() {
        // Clean up any running tests
        service.getActiveTestsStatus().keySet().each { testId ->
            service.stopTest(testId)
        }
        Thread.sleep(100)
    }

    def "should handle LINEAR ramp strategy"() {
        given: "a config with LINEAR ramp strategy"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setStartingConcurrency(5)
        config.setMaxConcurrency(20)
        config.setRampStrategyType(RampStrategyType.LINEAR)
        config.setRampDurationSeconds(2L)
        config.setTestDurationSeconds(3)

        when: "starting a test with LINEAR ramp"
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "test should be running with LINEAR strategy"
        testId != null
        status != null
        status.getConfiguration().getRampStrategyType() == RampStrategyType.LINEAR
        status.getConfiguration().getRampDurationSeconds() == 2L

        cleanup:
        service.stopTest(testId)
    }

    def "should handle RATE_LIMITED mode"() {
        given: "a config with RATE_LIMITED mode"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.RATE_LIMITED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setStartingConcurrency(10)
        config.setMaxConcurrency(50)
        config.setMaxTpsLimit(100)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(10)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(3)

        when: "starting a test with RATE_LIMITED mode"
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "test should be running with TPS limit"
        testId != null
        status != null
        status.getConfiguration().getMode() == LoadTestMode.RATE_LIMITED
        status.getConfiguration().getMaxTpsLimit() == 100

        cleanup:
        service.stopTest(testId)
    }

    def "should handle CPU task type"() {
        given: "a config with CPU task type"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("CPU")
        config.setTaskParameter(100)
        config.setStartingConcurrency(5)
        config.setMaxConcurrency(10)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(5)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(2)

        when: "starting a CPU test"
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "test should be running with CPU tasks"
        testId != null
        status != null
        status.getConfiguration().getTaskType() == "CPU"

        cleanup:
        service.stopTest(testId)
    }

    def "should handle sustain duration in ramp strategy"() {
        given: "a config with sustain duration"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setStartingConcurrency(10)
        config.setMaxConcurrency(20)
        config.setRampStrategyType(RampStrategyType.LINEAR)
        config.setRampDurationSeconds(1L)
        config.setSustainDurationSeconds(2L)
        config.setTestDurationSeconds(5)

        when: "starting a test with sustain phase"
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "test should include sustain duration"
        testId != null
        status != null
        status.getConfiguration().getSustainDurationSeconds() == 2L

        cleanup:
        service.stopTest(testId)
    }

    def "should retrieve current metrics for active test"() {
        given: "a running test"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setStartingConcurrency(10)
        config.setMaxConcurrency(20)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(10)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(3)
        String testId = service.startTest(config)
        Thread.sleep(500)

        when: "getting current metrics"
        Object metrics = service.getCurrentMetrics(testId)

        then: "metrics should be returned"
        metrics != null

        cleanup:
        service.stopTest(testId)
    }

    def "should return null metrics for non-existent test"() {
        when: "getting metrics for non-existent test"
        Object metrics = service.getCurrentMetrics("non-existent-test-id")

        then: "should return null"
        metrics == null
    }

    def "should handle task parameter as String for SLEEP task"() {
        given: "a config with String task parameter"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter("50") // String instead of Integer
        config.setStartingConcurrency(5)
        config.setMaxConcurrency(10)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(5)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(2)

        when: "starting test with String parameter"
        String testId = service.startTest(config)
        Thread.sleep(300)

        then: "test should run successfully"
        testId != null
        service.getActiveTests().containsKey(testId)

        cleanup:
        service.stopTest(testId)
    }

    def "should handle completed test status query after test finishes"() {
        given: "a short test"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(1)
        config.setStartingConcurrency(2)
        config.setMaxConcurrency(2)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(2)
        config.setRampIntervalSeconds(1L)
        config.setTestDurationSeconds(1)

        when: "starting and waiting for completion"
        String testId = service.startTest(config)
        Thread.sleep(1500)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "status should show COMPLETED"
        status != null
        status.getStatus() == "COMPLETED"
        status.getTestId() == testId
    }

    def "should handle default parameter values when not specified"() {
        given: "a minimal config without optional parameters"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        // No starting concurrency, max concurrency, etc.
        config.setTestDurationSeconds(2)

        when: "starting test"
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "test should use default values"
        testId != null
        status != null

        cleanup:
        service.stopTest(testId)
    }

    def "should track elapsed time from start to end"() {
        given: "a running test"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setStartingConcurrency(5)
        config.setMaxConcurrency(10)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(5)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(3)
        String testId = service.startTest(config)

        when: "checking elapsed time after 1 second"
        Thread.sleep(1000)
        TestStatusResponse status1 = service.getTestStatus(testId)

        and: "checking again after 2 seconds total"
        Thread.sleep(1000)
        TestStatusResponse status2 = service.getTestStatus(testId)

        then: "elapsed time should increase"
        status1.getElapsedSeconds() >= 1
        status2.getElapsedSeconds() >= 2
        status2.getElapsedSeconds() > status1.getElapsedSeconds()

        cleanup:
        service.stopTest(testId)
    }

    def "should handle HTTP task with default URL parameter"() {
        given: "a config with HTTP task but no URL"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("HTTP")
        config.setTaskParameter(null) // Will use default URL
        config.setStartingConcurrency(2)
        config.setMaxConcurrency(5)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(3)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(2)

        when: "starting HTTP test"
        String testId = service.startTest(config)
        Thread.sleep(300)

        then: "test should start (may fail if service not available, but should not throw on start)"
        testId != null

        cleanup:
        service.stopTest(testId)
    }

    def "should preserve all configuration details in status response"() {
        given: "a fully configured test"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.RATE_LIMITED)
        config.setTaskType("CPU")
        config.setTaskParameter(200)
        config.setStartingConcurrency(15)
        config.setMaxConcurrency(75)
        config.setMaxTpsLimit(150)
        config.setRampStrategyType(RampStrategyType.LINEAR)
        config.setRampDurationSeconds(5L)
        config.setSustainDurationSeconds(3L)
        config.setTestDurationSeconds(10)

        when: "starting and checking status"
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then: "all configuration details should be preserved"
        status.getConfiguration().getMode() == LoadTestMode.RATE_LIMITED
        status.getConfiguration().getTaskType() == "CPU"
        status.getConfiguration().getTaskParameter() == 200
        status.getConfiguration().getStartingConcurrency() == 15
        status.getConfiguration().getMaxConcurrency() == 75
        status.getConfiguration().getMaxTpsLimit() == 150
        status.getConfiguration().getRampStrategyType() == RampStrategyType.LINEAR
        status.getConfiguration().getRampDurationSeconds() == 5L
        status.getConfiguration().getSustainDurationSeconds() == 3L
        status.getConfiguration().getTestDurationSeconds() == 10

        cleanup:
        service.stopTest(testId)
    }

    def "should handle case-insensitive task types"() {
        given: "configs with different case task types"
        def configs = [
            createConfigWithTaskType("sleep"),
            createConfigWithTaskType("SLEEP"),
            createConfigWithTaskType("Sleep"),
            createConfigWithTaskType("cpu"),
            createConfigWithTaskType("CPU")
        ]

        when: "starting tests with different case task types"
        def testIds = configs.collect { service.startTest(it) }
        Thread.sleep(300)

        then: "all tests should start successfully"
        testIds.every { it != null }
        testIds.every { service.getActiveTests().containsKey(it) || service.getTestStatus(it)?.getStatus() == "COMPLETED" }

        cleanup:
        testIds.each { service.stopTest(it) }
    }

    def "should handle unknown task type with default behavior"() {
        given: "a config with unknown task type"
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType("UNKNOWN_TASK_TYPE")
        config.setTaskParameter(10)
        config.setStartingConcurrency(5)
        config.setMaxConcurrency(10)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(5)
        config.setRampIntervalSeconds(5L)
        config.setTestDurationSeconds(2)

        when: "starting test with unknown task type"
        String testId = service.startTest(config)
        Thread.sleep(300)

        then: "should fall back to default behavior (SLEEP)"
        testId != null
        service.getActiveTests().containsKey(testId)

        cleanup:
        service.stopTest(testId)
    }

    private TestConfigRequest createConfigWithTaskType(String taskType) {
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setTaskType(taskType)
        config.setTaskParameter(10)
        config.setStartingConcurrency(3)
        config.setMaxConcurrency(5)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(2)
        config.setRampIntervalSeconds(3L)
        config.setTestDurationSeconds(2)
        return config
    }
}
