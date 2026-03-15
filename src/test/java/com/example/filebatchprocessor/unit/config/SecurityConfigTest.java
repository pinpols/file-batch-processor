package com.example.filebatchprocessor.unit.config;

import com.example.filebatchprocessor.config.SecurityConfig;
import com.example.filebatchprocessor.config.OpsSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void shouldBuildUsersWithExpectedRoles() {
        OpsSecurityProperties properties = new OpsSecurityProperties();
        properties.getViewer().setUsername("viewer");
        properties.getViewer().setPassword("{noop}viewer");
        properties.getOperator().setUsername("operator");
        properties.getOperator().setPassword("{noop}operator");
        properties.getAdmin().setUsername("admin");
        properties.getAdmin().setPassword("{noop}admin");

        UserDetailsService userDetailsService = securityConfig.userDetailsService(properties);

        assertTrue(userDetailsService.loadUserByUsername("viewer")
                .getAuthorities().stream().anyMatch(a -> "ROLE_VIEWER".equals(a.getAuthority())));
        assertTrue(userDetailsService.loadUserByUsername("operator")
                .getAuthorities().stream().anyMatch(a -> "ROLE_OPERATOR".equals(a.getAuthority())));
        assertTrue(userDetailsService.loadUserByUsername("admin")
                .getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
}

