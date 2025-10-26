package com.vajraedge.perftest.service

import com.vajraedge.perftest.dto.TestConfigRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class TestExecutionServiceSimpleSpec extends Specification {

    @Autowired
    TestExecutionService service

    def "should inject service successfully"() {
        expect:
        service != null
    }

    def "should start test with valid configuration"() {
        given: "a valid test configuration"
        TestConfigRequest config = new TestConfigRequest()
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setMaxConcurrency(5)
        config.setTargetTps(50)
        config.setTestDurationSeconds(2)
        config.setRampUpDurationSeconds(0)

        when: "test is started"
        String testId = service.startTest(config)

        then: "test ID is returned"
        testId != null
        testId.length() > 0

        cleanup:
        if (testId != null) {
            service.stopTest(testId)
        }
    }

    def "should get test status"() {
        given: "a running test"
        TestConfigRequest config = new TestConfigRequest()
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setMaxConcurrency(5)
        config.setTargetTps(50)
        config.setTestDurationSeconds(5)
        config.setRampUpDurationSeconds(0)
        String testId = service.startTest(config)

        when: "status is queried"
        Thread.sleep(500)
        def status = service.getTestStatus(testId)

        then: "status is available"
        status != null
        status.testId == testId

        cleanup:
        service.stopTest(testId)
    }

    def "should stop running test"() {
        given: "a running test"
        TestConfigRequest config = new TestConfigRequest()
        config.setTaskType("SLEEP")
        config.setTaskParameter(10)
        config.setMaxConcurrency(5)
        config.setTargetTps(50)
        config.setTestDurationSeconds(10)
        config.setRampUpDurationSeconds(0)
        String testId = service.startTest(config)
        Thread.sleep(500)

        when: "test is stopped"
        boolean stopped = service.stopTest(testId)

        then: "stop is successful"
        stopped == true
    }

    def "should get active tests status"() {
        when: "querying active tests"
        Map<String, String> activeTests = service.getActiveTestsStatus()

        then: "map is returned"
        activeTests != null
    }
}
