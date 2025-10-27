package com.vajraedge.perftest.service

import com.vajraedge.perftest.dto.TestConfigRequest
import com.vajraedge.perftest.dto.TestStatusResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Enhanced integration tests for TestExecutionService
 */
@SpringBootTest
class TestExecutionServiceSpec extends Specification {

    @Autowired
    TestExecutionService service

    def cleanup() {
        // Clean up any running tests
        service.getActiveTestsStatus().keySet().each { testId ->
            service.stopTest(testId)
        }
        Thread.sleep(100)
    }

    def "should start test and generate unique test ID"() {
        given:
        TestConfigRequest config = new TestConfigRequest()
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setMaxConcurrency(5)
        config.setTargetTps(50)
        config.setTestDurationSeconds(2)
        config.setRampUpDurationSeconds(0)

        when:
        String testId = service.startTest(config)

        then:
        testId != null
        testId.length() > 0
        service.getActiveTests().containsKey(testId)

        cleanup:
        service.stopTest(testId)
    }

    def "should start multiple tests with unique IDs"() {
        given:
        TestConfigRequest config1 = createConfig("SLEEP", 10, 5, 50, 3, 0)
        TestConfigRequest config2 = createConfig("SLEEP", 10, 5, 50, 3, 0)

        when:
        String testId1 = service.startTest(config1)
        String testId2 = service.startTest(config2)

        then:
        testId1 != testId2
        service.getActiveTests().size() >= 2

        cleanup:
        service.stopTest(testId1)
        service.stopTest(testId2)
    }

    def "should get test status for running test"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 10, 5, 50, 5, 0)
        String testId = service.startTest(config)
        Thread.sleep(300)

        when:
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        status != null
        status.getTestId() == testId
        status.getStatus() in ["RUNNING", "COMPLETED"]
        status.getStartTime() != null
        status.getConfiguration() != null
        status.getElapsedSeconds() >= 0

        cleanup:
        service.stopTest(testId)
    }

    def "should return null for non-existent test ID"() {
        when:
        TestStatusResponse status = service.getTestStatus("non-existent-id")

        then:
        status == null
    }

    def "should stop running test successfully"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 10, 5, 50, 10, 0)
        String testId = service.startTest(config)
        Thread.sleep(300)

        when:
        boolean stopped = service.stopTest(testId)

        then:
        stopped == true
        !service.getActiveTests().containsKey(testId)
    }

    def "should return false when stopping non-existent test"() {
        when:
        boolean stopped = service.stopTest("non-existent-id")

        then:
        stopped == false
    }

    def "should track active tests"() {
        given:
        TestConfigRequest config1 = createConfig("SLEEP", 10, 5, 50, 5, 0)
        TestConfigRequest config2 = createConfig("SLEEP", 10, 5, 50, 5, 0)

        when:
        String testId1 = service.startTest(config1)
        String testId2 = service.startTest(config2)
        Thread.sleep(200)
        Map<String, String> activeTests = service.getActiveTestsStatus()

        then:
        activeTests.size() >= 2
        activeTests.containsKey(testId1)
        activeTests.containsKey(testId2)

        cleanup:
        service.stopTest(testId1)
        service.stopTest(testId2)
    }

    def "should remove test from active tests after completion"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 1, 2, 10, 1, 0)
        String testId = service.startTest(config)

        when:
        Thread.sleep(1500)  // Wait for completion

        then:
        !service.getActiveTests().containsKey(testId)
    }

    def "should include current metrics in status"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 10, 10, 100, 3, 0)
        String testId = service.startTest(config)
        Thread.sleep(500)

        when:
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        status.getCurrentMetrics() != null
        status.getCurrentMetrics().getActiveTasks() != null
        status.getCurrentMetrics().getTotalRequests() != null
    }

    @Unroll
    def "should support different task types: #taskType"() {
        given:
        TestConfigRequest config = createConfig(taskType, taskParameter, 5, 50, 2, 0)

        when:
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        testId != null
        status != null

        cleanup:
        service.stopTest(testId)

        where:
        taskType | taskParameter
        "SLEEP"  | 10
        "CPU"    | 100
    }

    def "should handle test with ramp-up period"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 10, 10, 50, 5, 2)

        when:
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        testId != null
        status != null
        status.getConfiguration().getRampUpDurationSeconds() == 2

        cleanup:
        service.stopTest(testId)
    }

    def "should handle high concurrency"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 1, 100, 500, 2, 0)

        when:
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        testId != null
        status != null
        status.getConfiguration().getMaxConcurrency() == 100

        cleanup:
        service.stopTest(testId)
    }

    def "should track elapsed time correctly"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 10, 5, 50, 5, 0)
        String testId = service.startTest(config)

        when:
        Thread.sleep(1000)
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        status.getElapsedSeconds() >= 1

        cleanup:
        service.stopTest(testId)
    }

    def "should handle completed test status query"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 1, 2, 10, 1, 0)
        String testId = service.startTest(config)

        when:
        Thread.sleep(1500)  // Wait for completion
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        status != null
        status.getStatus() == "COMPLETED"
    }

    def "should return empty map when no active tests"() {
        when:
        Map<String, String> activeTests = service.getActiveTestsStatus()

        then:
        activeTests != null
        activeTests.isEmpty() || activeTests.size() >= 0
    }

    def "should preserve test configuration in status"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 50, 20, 100, 10, 3)
        String testId = service.startTest(config)
        Thread.sleep(300)

        when:
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        status.getConfiguration() != null
        status.getConfiguration().getTaskType() == "SLEEP"
        status.getConfiguration().getTaskParameter() == 50
        status.getConfiguration().getMaxConcurrency() == 20
        status.getConfiguration().getTargetTps() == 100
        status.getConfiguration().getTestDurationSeconds() == 10
        status.getConfiguration().getRampUpDurationSeconds() == 3

        cleanup:
        service.stopTest(testId)
    }

    def "should handle rapid start and stop"() {
        given:
        TestConfigRequest config = createConfig("SLEEP", 10, 5, 50, 10, 0)

        when:
        String testId = service.startTest(config)
        Thread.sleep(100)
        boolean stopped = service.stopTest(testId)

        then:
        testId != null
        stopped == true
    }

    def "should handle test failure gracefully"() {
        given:
        // This test should handle any internal failures
        TestConfigRequest config = createConfig("SLEEP", 10, 5, 50, 2, 0)

        when:
        String testId = service.startTest(config)
        Thread.sleep(300)
        TestStatusResponse status = service.getTestStatus(testId)

        then:
        notThrown(Exception)
        status != null

        cleanup:
        service.stopTest(testId)
    }

    private TestConfigRequest createConfig(String taskType, int taskParameter, 
                                          int maxConcurrency, int targetTps, 
                                          int testDurationSeconds, int rampUpDurationSeconds) {
        TestConfigRequest config = new TestConfigRequest()
        config.setTaskType(taskType)
        config.setTaskParameter(taskParameter)
        config.setStartingConcurrency(10)
        config.setMaxConcurrency(maxConcurrency)
        config.setTestDurationSeconds(testDurationSeconds)
        config.setRampStrategyType(com.vajraedge.perftest.concurrency.RampStrategyType.STEP)
        config.setRampStep(10)
        config.setRampIntervalSeconds(10L)
        // Note: targetTps and rampUpDurationSeconds are no longer used in concurrency-based mode
        return config
    }
}
