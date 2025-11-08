package net.vajraedge.perftest.suite

import net.vajraedge.perftest.dto.TestConfigRequest
import spock.lang.Specification

/**
 * Tests for TestSuite.
 */
class TestSuiteSpec extends Specification {
    
    def "should build suite with required fields"() {
        given: "scenarios"
        def scenario = TestScenario.builder()
            .scenarioId("s1")
            .name("Scenario 1")
            .config(new TestConfigRequest())
            .build()
        
        when: "building suite"
        def suite = TestSuite.builder()
            .suiteId("suite-1")
            .name("Load Test Suite")
            .addScenario(scenario)
            .build()
        
        then: "suite is created with defaults"
        suite.suiteId == "suite-1"
        suite.name == "Load Test Suite"
        suite.scenarioCount == 1
        suite.executionMode == ExecutionMode.SEQUENTIAL
        !suite.useCorrelation
    }
    
    def "should build suite with all fields"() {
        given: "multiple scenarios"
        def scenario1 = createScenario("s1", "Scenario 1")
        def scenario2 = createScenario("s2", "Scenario 2")
        
        when: "building with all fields"
        def suite = TestSuite.builder()
            .suiteId("suite-1")
            .name("Comprehensive Suite")
            .description("Full test suite")
            .addScenario(scenario1)
            .addScenario(scenario2)
            .executionMode(ExecutionMode.PARALLEL)
            .useCorrelation(true)
            .metadata("env", "staging")
            .metadata("region", "us-west")
            .build()
        
        then: "all fields are set"
        suite.suiteId == "suite-1"
        suite.name == "Comprehensive Suite"
        suite.description == "Full test suite"
        suite.scenarioCount == 2
        suite.executionMode == ExecutionMode.PARALLEL
        suite.useCorrelation
        suite.metadata.size() == 2
    }
    
    def "should reject suite without required fields"() {
        when: "building without suite ID"
        TestSuite.builder()
            .name("Test")
            .addScenario(createScenario("s1", "S1"))
            .build()
        
        then: "exception is thrown"
        def e = thrown(IllegalStateException)
        e.message.contains("Suite ID")
        
        when: "building without name"
        TestSuite.builder()
            .suiteId("suite-1")
            .addScenario(createScenario("s1", "S1"))
            .build()
        
        then: "exception is thrown"
        e = thrown(IllegalStateException)
        e.message.contains("name")
        
        when: "building without scenarios"
        TestSuite.builder()
            .suiteId("suite-1")
            .name("Test")
            .build()
        
        then: "exception is thrown"
        e = thrown(IllegalStateException)
        e.message.contains("scenario")
    }
    
    def "should support bulk scenario addition"() {
        given: "a list of scenarios"
        def scenarios = [
            createScenario("s1", "S1"),
            createScenario("s2", "S2"),
            createScenario("s3", "S3")
        ]
        
        when: "adding all at once"
        def suite = TestSuite.builder()
            .suiteId("suite-1")
            .name("Bulk Suite")
            .scenarios(scenarios)
            .build()
        
        then: "all scenarios are added"
        suite.scenarioCount == 3
        suite.scenarios.size() == 3
    }
    
    def "should provide defensive copy of scenarios"() {
        given: "a suite"
        def suite = TestSuite.builder()
            .suiteId("suite-1")
            .name("Test")
            .addScenario(createScenario("s1", "S1"))
            .build()
        
        when: "modifying returned scenarios"
        def scenarios = suite.getScenarios()
        scenarios.add(createScenario("s2", "S2"))
        
        then: "original is not modified"
        suite.scenarioCount == 1
    }
    
    def "should provide defensive copy of metadata"() {
        given: "a suite with metadata"
        def suite = TestSuite.builder()
            .suiteId("suite-1")
            .name("Test")
            .addScenario(createScenario("s1", "S1"))
            .metadata("key", "value")
            .build()
        
        when: "modifying returned metadata"
        def metadata = suite.getMetadata()
        metadata.put("newKey", "newValue")
        
        then: "original is not modified"
        !suite.getMetadata().containsKey("newKey")
    }
    
    private TestScenario createScenario(String id, String name) {
        TestScenario.builder()
            .scenarioId(id)
            .name(name)
            .config(new TestConfigRequest())
            .build()
    }
}
