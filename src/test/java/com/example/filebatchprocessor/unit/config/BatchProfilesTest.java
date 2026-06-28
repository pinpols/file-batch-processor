package com.example.filebatchprocessor.unit.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.config.BatchProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BatchProfilesTest {

    @Test
    void emptyProfilesAreProductionLike() {
        assertTrue(BatchProfiles.isProductionLike(new MockEnvironment()));
    }

    @Test
    void testProfileIsNotProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        assertFalse(BatchProfiles.isProductionLike(env));
    }

    @Test
    void devAndLocalAreNotProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local");
        assertFalse(BatchProfiles.isProductionLike(env));
    }

    @Test
    void prodIsProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertTrue(BatchProfiles.isProductionLike(env));
    }

    @Test
    void stagingIsProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        assertTrue(BatchProfiles.isProductionLike(env));
    }

    @Test
    void uatIsProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("uat");
        assertTrue(BatchProfiles.isProductionLike(env));
    }

    @Test
    void unknownQaProfileIsProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("qa");
        assertTrue(BatchProfiles.isProductionLike(env));
    }

    @Test
    void mixedTestAndProdIsProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test", "prod");
        assertTrue(BatchProfiles.isProductionLike(env));
    }
}
