package com.vajraedge.perftest.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(HealthController)
class HealthControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    def "should return UP status on health check"() {
        when: "GET request to health endpoint"
        def result = mockMvc.perform(get("/api/health"))

        then: "UP status is returned"
        result.andExpect(status().isOk())
              .andExpect(jsonPath('$.status').value("UP"))
    }

    def "should return timestamp in health check"() {
        when: "GET request to health endpoint"
        def result = mockMvc.perform(get("/api/health"))

        then: "timestamp is present"
        result.andExpect(status().isOk())
              .andExpect(jsonPath('$.timestamp').exists())
    }

    def "should handle multiple concurrent health checks"() {
        when: "multiple health check requests"
        def results = (1..10).collect {
            mockMvc.perform(get("/api/health"))
        }

        then: "all return UP status"
        results.every { result ->
            result.andReturn().response.status == 200
        }
    }
}
