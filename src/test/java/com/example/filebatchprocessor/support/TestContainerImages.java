package com.example.filebatchprocessor.support;

/** Testcontainers image tags used by integration and e2e tests. */
final class TestContainerImages {

    /** Keep in sync with docker-compose.dev.yml and docker-compose.prod.yml. */
    static final String POSTGRES = "postgres:17";

    private TestContainerImages() {}
}
