package net.vajraedge.perftest.controller;

import net.vajraedge.perftest.dto.TestConfigRequest;
import net.vajraedge.perftest.validation.PreFlightValidator;
import net.vajraedge.perftest.validation.ValidationContext;
import net.vajraedge.perftest.validation.ValidationResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for pre-flight validation.
 */
@RestController
@RequestMapping("/api/validation")
@CrossOrigin(origins = "*")
public class ValidationController {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationController.class);
    
    private final PreFlightValidator validator;
    
    public ValidationController(PreFlightValidator validator) {
        this.validator = validator;
    }
    
    /**
     * Validate test configuration before execution.
     *
     * @param config the test configuration to validate
     * @return validation result with status and check details
     */
    @PostMapping
    public ResponseEntity<ValidationResult> validate(@RequestBody @Valid TestConfigRequest config) {
        log.info("Received validation request for task type: {}", config.getTaskType());
        
        ValidationContext context = ValidationContext.builder()
            .config(config)
            .build();
        
        ValidationResult result = validator.validate(context);
        
        return ResponseEntity.ok(result);
    }
}
