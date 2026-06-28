package com.example.filebatchprocessor.unit.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.filebatchprocessor.config.BatchIoSafetyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BatchIoSafetyConfigTest {

    @Test
    void rejectsMissingBaseDirsInProductionLikeProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        BatchIoSafetyConfig config = new BatchIoSafetyConfig(environment, "", "");

        assertThatThrownBy(config::validateProductionBaseDirs)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch.io.input-base-dir");
    }

    @Test
    void allowsMissingBaseDirsInKnownNonProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        BatchIoSafetyConfig config = new BatchIoSafetyConfig(environment, "", "");

        assertThatCode(config::validateProductionBaseDirs).doesNotThrowAnyException();
    }
}
