package com.example.filebatchprocessor.unit.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.filebatchprocessor.config.OpsSecurityProperties;
import com.example.filebatchprocessor.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringJUnitWebConfig(classes = {SecurityConfig.class, SecurityConfigTest.TestSecurityWebConfig.class})
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "ops.security.viewer.username=viewer",
            "ops.security.viewer.password={noop}viewer",
            "ops.security.operator.username=operator",
            "ops.security.operator.password={noop}operator",
            "ops.security.admin.username=admin",
            "ops.security.admin.password={noop}admin"
        })
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldBuildUsersWithExpectedRoles() {
        OpsSecurityProperties properties = new OpsSecurityProperties();
        properties.getViewer().setUsername("viewer");
        properties.getViewer().setPassword("{noop}viewer");
        properties.getOperator().setUsername("operator");
        properties.getOperator().setPassword("{noop}operator");
        properties.getAdmin().setUsername("admin");
        properties.getAdmin().setPassword("{noop}admin");

        org.springframework.mock.env.MockEnvironment environment = new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("test"); // 非生产:弱口令仅告警不阻断
        UserDetailsService userDetailsService = securityConfig.userDetailsService(properties, environment);

        assertTrue(userDetailsService.loadUserByUsername("viewer").getAuthorities().stream()
                .anyMatch(a -> "ROLE_VIEWER".equals(a.getAuthority())));
        assertTrue(userDetailsService.loadUserByUsername("operator").getAuthorities().stream()
                .anyMatch(a -> "ROLE_OPERATOR".equals(a.getAuthority())));
        assertTrue(userDetailsService.loadUserByUsername("admin").getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void viewerCannotCallMutatingOpsBatchEndpoint() throws Exception {
        mockMvc.perform(post("/ops/batch/rerun")
                        .with(httpBasic("viewer", "viewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"task-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotCallMutatingFileDispatchEndpoint() throws Exception {
        mockMvc.perform(post("/ops/file-dispatch/10/resend")
                        .with(httpBasic("viewer", "viewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotCallMutatingFileReprocessEndpoint() throws Exception {
        mockMvc.perform(post("/ops/files/10/reprocess")
                        .with(httpBasic("viewer", "viewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @org.springframework.context.annotation.Configuration
    @EnableWebSecurity
    static class TestSecurityWebConfig {}
}
