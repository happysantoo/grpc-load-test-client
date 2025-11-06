package com.vajraedge.perftest.validation

import com.vajraedge.perftest.dto.TestConfigRequest
import spock.lang.Specification

class ValidationContextSpec extends Specification {
    
    def "should create validation context with config"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxConcurrency(100)
        config.setStartingConcurrency(10)
        config.setTestDurationSeconds(300L)
        config.setTaskParameter("http://localhost:8080")
        
        when:
        def context = ValidationContext.builder()
            .config(config)
            .build()
        
        then:
        context.config == config
        context.taskType == "HTTP"
        context.maxConcurrency == 100
        context.startingConcurrency == 10
        context.testDurationSeconds == 300L
        context.taskParameter == "http://localhost:8080"
    }
    
    def "should store and retrieve additional context"() {
        given:
        def config = new TestConfigRequest()
        
        when:
        def context = ValidationContext.builder()
            .config(config)
            .addContext("key1", "value1")
            .addContext("key2", 123)
            .build()
        
        then:
        context.getAdditionalContext("key1") == "value1"
        context.getAdditionalContext("key2") == 123
        context.getAdditionalContext("nonexistent") == null
    }
    
    def "should throw exception when building without config"() {
        when:
        ValidationContext.builder().build()
        
        then:
        thrown(IllegalStateException)
    }
    
    def "should handle null task parameter"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskParameter(null)
        
        when:
        def context = ValidationContext.builder()
            .config(config)
            .build()
        
        then:
        context.taskParameter == null
    }
    
    def "should expose TPS limit from config"() {
        given:
        def config = new TestConfigRequest()
        config.setMaxTpsLimit(5000)
        
        when:
        def context = ValidationContext.builder()
            .config(config)
            .build()
        
        then:
        context.maxTpsLimit == 5000
    }
}
