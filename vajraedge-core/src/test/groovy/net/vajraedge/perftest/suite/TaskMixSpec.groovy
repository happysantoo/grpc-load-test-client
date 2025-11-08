package net.vajraedge.perftest.suite

import spock.lang.Specification

/**
 * Tests for TaskMix.
 */
class TaskMixSpec extends Specification {
    
    def "should add tasks with weights"() {
        given: "a task mix"
        def taskMix = new TaskMix()
        
        when: "adding tasks"
        taskMix.addTask("HTTP_GET", 70)
        taskMix.addTask("HTTP_POST", 20)
        taskMix.addTask("HTTP_DELETE", 10)
        
        then: "weights are stored"
        taskMix.getWeights().size() == 3
        taskMix.getWeights()["HTTP_GET"] == 70
        taskMix.getWeights()["HTTP_POST"] == 20
        taskMix.getWeights()["HTTP_DELETE"] == 10
    }
    
    def "should calculate correct percentages"() {
        given: "a task mix with weights"
        def taskMix = new TaskMix()
        taskMix.addTask("READ", 70)
        taskMix.addTask("WRITE", 20)
        taskMix.addTask("DELETE", 10)
        
        expect: "correct percentages"
        taskMix.getPercentage("READ") == 70.0
        taskMix.getPercentage("WRITE") == 20.0
        taskMix.getPercentage("DELETE") == 10.0
    }
    
    def "should select tasks according to distribution"() {
        given: "a task mix"
        def taskMix = new TaskMix()
        taskMix.addTask("HIGH", 80)
        taskMix.addTask("LOW", 20)
        
        when: "selecting many tasks"
        def selections = (1..1000).collect { taskMix.selectTask() }
        def counts = selections.countBy { it }
        
        then: "distribution should approximate the weights"
        counts["HIGH"] > 700 // Should be around 800 +/- margin
        counts["HIGH"] < 900
        counts["LOW"] > 100  // Should be around 200 +/- margin
        counts["LOW"] < 300
    }
    
    def "should reject zero or negative weights"() {
        given: "a task mix"
        def taskMix = new TaskMix()
        
        when: "adding task with invalid weight"
        taskMix.addTask("TASK", weight)
        
        then: "exception is thrown"
        thrown(IllegalArgumentException)
        
        where:
        weight << [0, -1, -100]
    }
    
    def "should throw exception when selecting from empty mix"() {
        given: "an empty task mix"
        def taskMix = new TaskMix()
        
        when: "selecting a task"
        taskMix.selectTask()
        
        then: "exception is thrown"
        thrown(IllegalStateException)
    }
    
    def "should report empty status correctly"() {
        given: "an empty task mix"
        def taskMix = new TaskMix()
        
        expect:
        taskMix.isEmpty()
        taskMix.size() == 0
        
        when: "adding a task"
        taskMix.addTask("TASK", 100)
        
        then:
        !taskMix.isEmpty()
        taskMix.size() == 1
    }
    
    def "should return zero percentage for unknown task"() {
        given: "a task mix"
        def taskMix = new TaskMix()
        taskMix.addTask("KNOWN", 100)
        
        expect:
        taskMix.getPercentage("UNKNOWN") == 0.0
    }
}
