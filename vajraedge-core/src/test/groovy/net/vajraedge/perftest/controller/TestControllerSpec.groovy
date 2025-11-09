package net.vajraedge.perftest.controller

import net.vajraedge.perftest.distributed.WorkerManager
import net.vajraedge.perftest.dto.TestConfigRequest
import net.vajraedge.perftest.dto.TestStatusResponse
import net.vajraedge.perftest.sdk.plugin.PluginRegistry
import net.vajraedge.perftest.service.DistributedTestService
import net.vajraedge.perftest.service.TestExecutionService
import net.vajraedge.perftest.validation.PreFlightValidator
import net.vajraedge.perftest.validation.ValidationResult
import net.vajraedge.perftest.validation.CheckResult
import net.vajraedge.perftest.concurrency.LoadTestMode
import net.vajraedge.perftest.concurrency.RampStrategyType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.when
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Tests for TestController REST API
 */
@WebMvcTest(TestController)
class TestControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @MockBean
    TestExecutionService testExecutionService

    @MockBean
    PreFlightValidator preFlightValidator
    
    @MockBean
    PluginRegistry pluginRegistry
    
    @MockBean
    DistributedTestService distributedTestService
    
    @MockBean
    WorkerManager workerManager

    @Autowired
    ObjectMapper objectMapper
    
    def setup() {
        // Default: validation passes
        ValidationResult passResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Default Check", "Passed"))
            .build()
        when(preFlightValidator.validate(any())).thenReturn(passResult)
    }

    def "should start test with valid configuration"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(10)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        request.setTestDurationSeconds(60)
        
        String requestJson = objectMapper.writeValueAsString(request)
        when(testExecutionService.startTest(any(TestConfigRequest))).thenReturn("test-123")

        when:
        def result = mockMvc.perform(
            post("/api/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )

        then:
        result.andExpect(status().isCreated())
              .andExpect(jsonPath('$.testId').value("test-123"))
              .andExpect(jsonPath('$.status').value("RUNNING"))
              .andExpect(jsonPath('$.message').value("Test started successfully"))
    }

    def "should reject invalid test configuration"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        // Set invalid values - maxConcurrency below minimum
        request.setMaxConcurrency(0)  // Invalid: must be >= 1
        String requestJson = objectMapper.writeValueAsString(request)

        when:
        def result = mockMvc.perform(
            post("/api/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )

        then:
        result.andExpect(status().isBadRequest())
    }

    def "should get test status by ID"() {
        given:
        String testId = "test-456"
        TestStatusResponse statusResponse = new TestStatusResponse(testId, "RUNNING")
        when(testExecutionService.getTestStatus(testId)).thenReturn(statusResponse)

        when:
        def result = mockMvc.perform(get("/api/tests/{id}", testId))

        then:
        result.andExpect(status().isOk())
              .andExpect(jsonPath('$.testId').value(testId))
              .andExpect(jsonPath('$.status').value("RUNNING"))
    }

    def "should return 404 for non-existent test"() {
        given:
        String testId = "non-existent"
        when(testExecutionService.getTestStatus(testId)).thenReturn(null)

        when:
        def result = mockMvc.perform(get("/api/tests/{id}", testId))

        then:
        result.andExpect(status().isNotFound())
              .andExpect(jsonPath('$.error').value("Test not found"))
              .andExpect(jsonPath('$.testId').value(testId))
    }

    def "should stop running test"() {
        given:
        String testId = "test-789"
        when(testExecutionService.stopTest(testId)).thenReturn(true)

        when:
        def result = mockMvc.perform(delete("/api/tests/{id}", testId))

        then:
        result.andExpect(status().isOk())
              .andExpect(jsonPath('$.testId').value(testId))
              .andExpect(jsonPath('$.status').value("STOPPED"))
              .andExpect(jsonPath('$.message').value("Test stopped successfully"))
    }

    def "should return 404 when stopping non-existent test"() {
        given:
        String testId = "non-existent"
        when(testExecutionService.stopTest(testId)).thenReturn(false)

        when:
        def result = mockMvc.perform(delete("/api/tests/{id}", testId))

        then:
        result.andExpect(status().isNotFound())
              .andExpect(jsonPath('$.error').value("Test not found or already completed"))
              .andExpect(jsonPath('$.testId').value(testId))
    }

    def "should list all active tests"() {
        given:
        Map<String, String> activeTests = [
            "test-1": "RUNNING",
            "test-2": "RUNNING",
            "test-3": "COMPLETED"
        ]
        when(testExecutionService.getActiveTestsStatus()).thenReturn(activeTests)

        when:
        def result = mockMvc.perform(get("/api/tests"))

        then:
        result.andExpect(status().isOk())
              .andExpect(jsonPath('$.count').value(3))
              .andExpect(jsonPath('$.activeTests.test-1').value("RUNNING"))
              .andExpect(jsonPath('$.activeTests.test-2').value("RUNNING"))
              .andExpect(jsonPath('$.activeTests.test-3').value("COMPLETED"))
    }

    def "should list empty when no active tests"() {
        given:
        when(testExecutionService.getActiveTestsStatus()).thenReturn([:])

        when:
        def result = mockMvc.perform(get("/api/tests"))

        then:
        result.andExpect(status().isOk())
              .andExpect(jsonPath('$.count').value(0))
              .andExpect(jsonPath('$.activeTests').isEmpty())
    }

    def "should handle service exception when starting test"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(10)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        request.setTestDurationSeconds(60)
        
        String requestJson = objectMapper.writeValueAsString(request)
        when(testExecutionService.startTest(any(TestConfigRequest)))
            .thenThrow(new RuntimeException("System error"))

        when:
        def result = mockMvc.perform(
            post("/api/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )

        then:
        result.andExpect(status().isInternalServerError())
              .andExpect(jsonPath('$.error').exists())
    }

    @Unroll
    def "should validate maxConcurrency: #concurrency is #validity"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMaxConcurrency(concurrency)
        request.setStartingConcurrency(10)
        request.setTestDurationSeconds(60)
        request.setRampStrategyType(net.vajraedge.perftest.concurrency.RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        
        String requestJson = objectMapper.writeValueAsString(request)

        when:
        def result = mockMvc.perform(
            post("/api/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )

        then:
        result.andExpect(status()."${expectedStatus}"())

        where:
        concurrency || validity  | expectedStatus
        null        || "invalid" | "isBadRequest"
        0           || "invalid" | "isBadRequest"
        -1          || "invalid" | "isBadRequest"
        50001       || "invalid" | "isBadRequest"
    }
    
    def "should block test when validation fails"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(10)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        request.setTestDurationSeconds(60)
        
        // Validation fails
        ValidationResult failResult = ValidationResult.builder()
            .addCheckResult(CheckResult.fail("Config Check", "Invalid config", ["Detail"]))
            .build()
        when(preFlightValidator.validate(any())).thenReturn(failResult)
        
        String requestJson = objectMapper.writeValueAsString(request)

        when:
        def result = mockMvc.perform(
            post("/api/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )

        then:
        result.andExpect(status().isBadRequest())
              .andExpect(jsonPath('$.status').value("VALIDATION_FAILED"))
              .andExpect(jsonPath('$.message').exists())
              .andExpect(jsonPath('$.validation').exists())
    }
    
    def "should start test with validation warnings"() {
        given:
        TestConfigRequest request = new TestConfigRequest()
        request.setMode(LoadTestMode.CONCURRENCY_BASED)
        request.setStartingConcurrency(10)
        request.setMaxConcurrency(100)
        request.setRampStrategyType(RampStrategyType.STEP)
        request.setRampStep(10)
        request.setRampIntervalSeconds(30L)
        request.setTestDurationSeconds(60)
        
        // Validation has warnings
        ValidationResult warnResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Check 1", "Passed"))
            .addCheckResult(CheckResult.warn("Check 2", "Warning", ["Detail"]))
            .build()
        when(preFlightValidator.validate(any())).thenReturn(warnResult)
        when(testExecutionService.startTest(any(TestConfigRequest))).thenReturn("test-with-warnings")
        
        String requestJson = objectMapper.writeValueAsString(request)

        when:
        def result = mockMvc.perform(
            post("/api/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )

        then:
        result.andExpect(status().isCreated())
              .andExpect(jsonPath('$.testId').value("test-with-warnings"))
              .andExpect(jsonPath('$.status').value("RUNNING"))
              .andExpect(jsonPath('$.validation').exists())
    }
}
