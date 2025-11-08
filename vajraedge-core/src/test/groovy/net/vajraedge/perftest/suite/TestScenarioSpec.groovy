package net.vajraedge.perftest.suite

import net.vajraedge.perftest.dto.TestConfigRequest
import spock.lang.Specification

/**
 * Tests for TestScenario.
 */
class TestScenarioSpec extends Specification {
    
    def "should build scenario with required fields"() {
        given: "scenario configuration"
        def config = new TestConfigRequest()
        config.setTaskType("HTTP_GET")
        config.setTaskParameter("https://example.com")
        
        when: "building scenario"
        def scenario = TestScenario.builder()
            .scenarioId("scenario-1")
            .name("Load Test")
            .config(config)
            .build()
        
        then: "scenario is created"
        scenario.scenarioId == "scenario-1"
        scenario.name == "Load Test"
        scenario.config == config
    }
    
    def "should build scenario with all fields"() {
        given: "full scenario configuration"
        def config = new TestConfigRequest()
        def taskMix = new TaskMix()
        taskMix.addTask("GET", 70)
        taskMix.addTask("POST", 30)
        
        when: "building with all fields"
        def scenario = TestScenario.builder()
            .scenarioId("scenario-1")
            .name("Mixed Load")
            .description("Test with mixed operations")
            .config(config)
            .taskMix(taskMix)
            .metadata("priority", "high")
            .metadata("owner", "team-a")
            .build()
        
        then: "all fields are set"
        scenario.scenarioId == "scenario-1"
        scenario.name == "Mixed Load"
        scenario.description == "Test with mixed operations"
        scenario.hasTaskMix()
        scenario.metadata.size() == 2
        scenario.metadata["priority"] == "high"
    }
    
    def "should reject scenario without required fields"() {
        when: "building without scenario ID"
        TestScenario.builder()
            .name("Test")
            .config(new TestConfigRequest())
            .build()
        
        then: "exception is thrown"
        def e = thrown(IllegalStateException)
        e.message.contains("Scenario ID")
        
        when: "building without name"
        TestScenario.builder()
            .scenarioId("scenario-1")
            .config(new TestConfigRequest())
            .build()
        
        then: "exception is thrown"
        e = thrown(IllegalStateException)
        e.message.contains("name")
        
        when: "building without config"
        TestScenario.builder()
            .scenarioId("scenario-1")
            .name("Test")
            .build()
        
        then: "exception is thrown"
        e = thrown(IllegalStateException)
        e.message.contains("configuration")
    }
    
    def "should report task mix presence correctly"() {
        given: "a config"
        def config = new TestConfigRequest()
        
        expect: "scenario without task mix"
        def simple = TestScenario.builder()
            .scenarioId("s1")
            .name("Simple")
            .config(config)
            .build()
        !simple.hasTaskMix()
        
        and: "scenario with empty task mix"
        def withEmpty = TestScenario.builder()
            .scenarioId("s2")
            .name("Empty Mix")
            .config(config)
            .taskMix(new TaskMix())
            .build()
        !withEmpty.hasTaskMix()
        
        and: "scenario with populated task mix"
        def mix = new TaskMix()
        mix.addTask("GET", 100)
        def withMix = TestScenario.builder()
            .scenarioId("s3")
            .name("With Mix")
            .config(config)
            .taskMix(mix)
            .build()
        withMix.hasTaskMix()
    }
    
    def "should provide defensive copy of metadata"() {
        given: "a scenario with metadata"
        def scenario = TestScenario.builder()
            .scenarioId("s1")
            .name("Test")
            .config(new TestConfigRequest())
            .metadata("key", "value")
            .build()
        
        when: "modifying returned metadata"
        def metadata = scenario.getMetadata()
        metadata.put("newKey", "newValue")
        
        then: "original is not modified"
        !scenario.getMetadata().containsKey("newKey")
    }
}
