package net.vajraedge.perftest.dto

import net.vajraedge.perftest.concurrency.LoadTestMode
import net.vajraedge.perftest.concurrency.RampStrategyType
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for TestConfigRequest DTO validation
 */
class TestConfigRequestSpec extends Specification {

    Validator validator

    def setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory()
        validator = factory.getValidator()
    }

    def "should create valid test config request with all fields"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(10)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        request.setTestDurationSeconds(60)
        request.setTaskType("SLEEP")
        request.setTaskParameter(10)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
        request.getMode() == LoadTestMode.CONCURRENCY_BASED
        request.getStartingConcurrency() == 10
        request.getMaxConcurrency() == 100
        request.getRampStrategyType() == RampStrategyType.STEP
        request.getTestDurationSeconds() == 60
        request.getTaskType() == "SLEEP"
        request.getTaskParameter() == 10
    }

    @Unroll
    def "should validate maxConcurrency: value=#value, shouldBeValid=#shouldBeValid"() {
        given:
        TestConfigRequest request = createValidConfig()
        request.setMaxConcurrency(value)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty() == shouldBeValid

        where:
        value  || shouldBeValid
        null   || false  // @NotNull
        0      || false  // @Min(1)
        1      || true   // Valid
        1000   || true   // Valid
        10000  || true   // Valid
        50000  || true   // @Max(50000)
        50001  || false  // Exceeds max
    }

    @Unroll
    def "should validate testDurationSeconds: value=#value, shouldBeValid=#shouldBeValid"() {
        given:
        TestConfigRequest request = createValidConfig()
        request.setTestDurationSeconds(value)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty() == shouldBeValid

        where:
        value || shouldBeValid
        null  || false  // @NotNull
        0     || false  // @Min(1)
        1     || true   // Valid
        60    || true   // Valid
        3600  || true   // Valid
    }

    def "should have default task type and parameter"() {
        when:
        TestConfigRequest request = new TestConfigRequest()

        then:
        request.getTaskType() == "HTTP"
        request.getTaskParameter() == "http://localhost:8081/api/products"
    }

    @Unroll
    def "should support custom task types: taskType=#taskType"() {
        given:
        TestConfigRequest request = createValidConfig()
        request.setTaskType(taskType)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
        request.getTaskType() == taskType

        where:
        taskType << ["SLEEP", "CPU_INTENSIVE", "GRPC_CALL", "HTTP_REQUEST"]
    }

    @Unroll
    def "should support custom task parameters: taskParameter=#taskParameter"() {
        given:
        TestConfigRequest request = createValidConfig()
        request.setTaskParameter(taskParameter)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
        request.getTaskParameter() == taskParameter

        where:
        taskParameter << [1, 10, 100, 1000]
    }

    def "should handle null task type"() {
        given:
        TestConfigRequest request = createValidConfig()
        request.setTaskType(null)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        !violations.isEmpty()  // taskType is @NotNull
    }

    def "should validate realistic test configuration"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(10)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.LINEAR)
        request.setRampDurationSeconds(30)
        request.setTestDurationSeconds(300)  // 5 minutes
        request.setTaskType("GRPC_CALL")
        request.setTaskParameter(50)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
    }

    def "should validate high-load test configuration"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(100)
        request.setMaxConcurrency(10000)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(100)
        request.setRampIntervalSeconds(10L)
        request.setTestDurationSeconds(60)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
    }

    def "should collect multiple validation errors"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        // Explicitly set required fields to null to trigger validation errors
        request.setMode(null)
        request.setMaxConcurrency(null)
        request.setTestDurationSeconds(null)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.size() >= 3  // mode, maxConcurrency, testDurationSeconds (all @NotNull)
    }

    private TestConfigRequest createValidConfig() {
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(1)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        request.setTestDurationSeconds(60)
        return request
    }
}
