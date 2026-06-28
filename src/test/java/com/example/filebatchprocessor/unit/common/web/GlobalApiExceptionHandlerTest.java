package com.example.filebatchprocessor.unit.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.filebatchprocessor.common.web.GlobalApiExceptionHandler;
import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.exception.RecordValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void businessNotFound_maps_to_404() throws Exception {
        mockMvc.perform(get("/throw/business-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void businessInvalidArgument_maps_to_400() throws Exception {
        mockMvc.perform(get("/throw/business-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void illegalArgument_maps_to_400() throws Exception {
        mockMvc.perform(get("/throw/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void illegalState_maps_to_409_conflict() throws Exception {
        mockMvc.perform(get("/throw/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void recordValidation_maps_to_400_validation_failed() throws Exception {
        mockMvc.perform(get("/throw/record-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unmappedRuntime_maps_to_500_sanitized() throws Exception {
        mockMvc.perform(get("/throw/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("internal error"));
    }

    @Test
    void duplicateKey_maps_to_409_conflict() throws Exception {
        mockMvc.perform(get("/throw/duplicate-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/business-not-found")
        void businessNotFound() {
            throw new BusinessException(ErrorCode.NOT_FOUND, "x");
        }

        @GetMapping("/throw/business-invalid")
        void businessInvalid() {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "bad arg");
        }

        @GetMapping("/throw/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("illegal");
        }

        @GetMapping("/throw/illegal-state")
        void illegalState() {
            throw new IllegalStateException("state conflict");
        }

        @GetMapping("/throw/record-validation")
        void recordValidation() {
            throw new RecordValidationException("row 3 invalid");
        }

        @GetMapping("/throw/runtime")
        void runtime() {
            throw new RuntimeException("super secret internal detail");
        }

        @GetMapping("/throw/duplicate-key")
        void duplicateKey() {
            throw new DuplicateKeyException("dup pk constraint xyz");
        }
    }
}
