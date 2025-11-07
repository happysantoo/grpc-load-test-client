package net.vajraedge.perftest.validation

import spock.lang.Specification

class ValidationResultSpec extends Specification {
    
    def "should build PASS result when all checks pass"() {
        given:
        def check1 = CheckResult.pass("Check 1", "Passed")
        def check2 = CheckResult.pass("Check 2", "Passed")
        
        when:
        def result = ValidationResult.builder()
            .addCheckResult(check1)
            .addCheckResult(check2)
            .build()
        
        then:
        result.status == ValidationResult.Status.PASS
        result.checkResults.size() == 2
        result.canProceed()
        result.summary.contains("2 checks")
        result.summary.contains("2 passed")
        result.summary.contains("0 warnings")
        result.summary.contains("0 failures")
    }
    
    def "should build WARN result when any check warns"() {
        given:
        def check1 = CheckResult.pass("Check 1", "Passed")
        def check2 = CheckResult.warn("Check 2", "Warning", ["Detail"])
        def check3 = CheckResult.pass("Check 3", "Passed")
        
        when:
        def result = ValidationResult.builder()
            .addCheckResult(check1)
            .addCheckResult(check2)
            .addCheckResult(check3)
            .build()
        
        then:
        result.status == ValidationResult.Status.WARN
        result.checkResults.size() == 3
        result.canProceed()
        result.summary.contains("3 checks")
        result.summary.contains("2 passed")
        result.summary.contains("1 warnings")
        result.summary.contains("0 failures")
    }
    
    def "should build FAIL result when any check fails"() {
        given:
        def check1 = CheckResult.pass("Check 1", "Passed")
        def check2 = CheckResult.warn("Check 2", "Warning", ["Detail"])
        def check3 = CheckResult.fail("Check 3", "Failed", ["Error"])
        
        when:
        def result = ValidationResult.builder()
            .addCheckResult(check1)
            .addCheckResult(check2)
            .addCheckResult(check3)
            .build()
        
        then:
        result.status == ValidationResult.Status.FAIL
        result.checkResults.size() == 3
        !result.canProceed()
        result.summary.contains("3 checks")
        result.summary.contains("1 passed")
        result.summary.contains("1 warnings")
        result.summary.contains("1 failures")
    }
    
    def "should handle multiple failures"() {
        given:
        def check1 = CheckResult.fail("Check 1", "Failed", ["Error 1"])
        def check2 = CheckResult.fail("Check 2", "Failed", ["Error 2"])
        
        when:
        def result = ValidationResult.builder()
            .addCheckResult(check1)
            .addCheckResult(check2)
            .build()
        
        then:
        result.status == ValidationResult.Status.FAIL
        !result.canProceed()
        result.summary.contains("2 failures")
    }
    
    def "should handle skip results"() {
        given:
        def check1 = CheckResult.pass("Check 1", "Passed")
        def check2 = CheckResult.skip("Check 2", "Skipped")
        
        when:
        def result = ValidationResult.builder()
            .addCheckResult(check1)
            .addCheckResult(check2)
            .build()
        
        then:
        result.status == ValidationResult.Status.PASS
        result.canProceed()
        result.checkResults.size() == 2
    }
    
    def "should return defensive copy of check results"() {
        given:
        def check1 = CheckResult.pass("Check 1", "Passed")
        def result = ValidationResult.builder()
            .addCheckResult(check1)
            .build()
        
        when:
        def retrievedResults = result.getCheckResults()
        retrievedResults.add(CheckResult.pass("Check 2", "Passed"))
        
        then:
        result.getCheckResults().size() == 1
    }
    
    def "should handle empty result set"() {
        when:
        def result = ValidationResult.builder().build()
        
        then:
        result.status == ValidationResult.Status.PASS
        result.canProceed()
        result.checkResults.isEmpty()
        result.summary.contains("0 checks")
    }
}
