package com.vajraedge.perftest.validation

import spock.lang.Specification

import java.time.Duration

class CheckResultSpec extends Specification {
    
    def "should create PASS result without duration"() {
        when:
        def result = CheckResult.pass("Test Check", "Check passed")
        
        then:
        result.checkName == "Test Check"
        result.status == CheckResult.Status.PASS
        result.message == "Check passed"
        result.duration == Duration.ZERO
        result.details.isEmpty()
    }
    
    def "should create PASS result with duration"() {
        given:
        def duration = Duration.ofMillis(150)
        
        when:
        def result = CheckResult.pass("Test Check", "Check passed", duration)
        
        then:
        result.checkName == "Test Check"
        result.status == CheckResult.Status.PASS
        result.message == "Check passed"
        result.duration == duration
        result.details.isEmpty()
    }
    
    def "should create PASS result with details"() {
        given:
        def details = ["Detail 1", "Detail 2"]
        
        when:
        def result = CheckResult.pass("Test Check", "Check passed", details)
        
        then:
        result.checkName == "Test Check"
        result.status == CheckResult.Status.PASS
        result.message == "Check passed"
        result.duration == Duration.ZERO
        result.details == details
    }
    
    def "should create WARN result with details"() {
        given:
        def details = ["Warning 1", "Warning 2"]
        
        when:
        def result = CheckResult.warn("Test Check", "Check has warnings", details)
        
        then:
        result.checkName == "Test Check"
        result.status == CheckResult.Status.WARN
        result.message == "Check has warnings"
        result.details == details
    }
    
    def "should create FAIL result with details"() {
        given:
        def details = ["Error 1", "Error 2"]
        
        when:
        def result = CheckResult.fail("Test Check", "Check failed", details)
        
        then:
        result.checkName == "Test Check"
        result.status == CheckResult.Status.FAIL
        result.message == "Check failed"
        result.details == details
    }
    
    def "should create SKIP result"() {
        when:
        def result = CheckResult.skip("Test Check", "Not applicable")
        
        then:
        result.checkName == "Test Check"
        result.status == CheckResult.Status.SKIP
        result.message == "Not applicable"
        result.details.isEmpty()
    }
    
    def "should return defensive copy of details"() {
        given:
        def originalDetails = ["Detail 1", "Detail 2"]
        def result = CheckResult.pass("Test Check", "Check passed", originalDetails)
        
        when:
        def retrievedDetails = result.getDetails()
        retrievedDetails.add("Detail 3")
        
        then:
        result.getDetails().size() == 2
        !result.getDetails().contains("Detail 3")
    }
}
