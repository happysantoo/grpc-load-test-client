package com.vajraedge.perftest.dto

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
        request.setTargetTps(100)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(5)
        request.setTaskType("SLEEP")
        request.setTaskParameter(10)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
        request.getTargetTps() == 100
        request.getMaxConcurrency() == 10
        request.getTestDurationSeconds() == 60
        request.getRampUpDurationSeconds() == 5
        request.getTaskType() == "SLEEP"
        request.getTaskParameter() == 10
    }

    @Unroll
    def "should validate targetTps: value=#value, shouldBeValid=#shouldBeValid"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(value)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(0)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty() == shouldBeValid

        where:
        value    || shouldBeValid
        null     || false  // @NotNull
        0        || false  // @Min(1)
        1        || true   // Valid
        100      || true   // Valid
        100000   || true   // @Max(100000)
        100001   || false  // Exceeds max
    }

    @Unroll
    def "should validate maxConcurrency: value=#value, shouldBeValid=#shouldBeValid"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(100)
        request.setMaxConcurrency(value)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(0)

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
        50000  || true   // @Max(50000)
        50001  || false  // Exceeds max
    }

    @Unroll
    def "should validate testDurationSeconds: value=#value, shouldBeValid=#shouldBeValid"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(100)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(value)
        request.setRampUpDurationSeconds(0)

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

    @Unroll
    def "should validate rampUpDurationSeconds: value=#value, shouldBeValid=#shouldBeValid"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(100)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(value)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty() == shouldBeValid

        where:
        value || shouldBeValid
        null  || false  // @NotNull
        -1    || false  // @Min(0)
        0     || true   // Valid (no ramp-up)
        5     || true   // Valid
        60    || true   // Valid
    }

    def "should have default task type and parameter"() {
        when:
        TestConfigRequest request = new TestConfigRequest()

        then:
        request.getTaskType() == "SLEEP"
        request.getTaskParameter() == 10
    }

    @Unroll
    def "should support custom task types: taskType=#taskType"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(100)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(0)
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
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(100)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(0)
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
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(100)
        request.setMaxConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(0)
        request.setTaskType(null)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()  // No @NotNull on taskType
    }

    def "should validate realistic test configuration"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setTargetTps(1000)
        request.setMaxConcurrency(100)
        request.setTestDurationSeconds(300)  // 5 minutes
        request.setRampUpDurationSeconds(30)
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
        request.setTargetTps(50000)
        request.setMaxConcurrency(10000)
        request.setTestDurationSeconds(60)
        request.setRampUpDurationSeconds(10)

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.isEmpty()
    }

    def "should collect multiple validation errors"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        // Leave all required fields null

        when:
        Set<ConstraintViolation<TestConfigRequest>> violations = validator.validate(request)

        then:
        violations.size() == 4  // targetTps, maxConcurrency, testDurationSeconds, rampUpDurationSeconds
    }
}
