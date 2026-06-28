package com.example.filebatchprocessor.unit.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.config.SecurityCredentials;
import org.junit.jupiter.api.Test;

class SecurityCredentialsTest {

    @Test
    void nullIsWeak() {
        assertTrue(SecurityCredentials.isWeak(null));
    }

    @Test
    void blankIsWeak() {
        assertTrue(SecurityCredentials.isWeak("   "));
    }

    @Test
    void changeMePlaceholderIsWeak() {
        assertTrue(SecurityCredentials.isWeak("{noop}change_me_viewer"));
    }

    @Test
    void replaceWithStrongPlaceholderIsWeak() {
        assertTrue(SecurityCredentials.isWeak("{noop}replace_with_strong_admin_password"));
    }

    @Test
    void yourSecurePasswordPlaceholderIsWeak() {
        assertTrue(SecurityCredentials.isWeak("your_secure_password_here"));
    }

    @Test
    void shortNoopPlaintextIsWeak() {
        assertTrue(SecurityCredentials.isWeak("{noop}short"));
    }

    @Test
    void strongNoopPassphraseIsNotWeak() {
        assertFalse(SecurityCredentials.isWeak("{noop}A-Strong-Passphrase-9f3x7q2z"));
    }

    @Test
    void bcryptHashIsNotWeak() {
        assertFalse(SecurityCredentials.isWeak(
                "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
    }
}
