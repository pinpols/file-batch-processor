package com.example.filebatchprocessor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(OpsSecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OpsSecurityProperties properties) throws Exception {
        if (!properties.isEnabled()) {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/ops/console", "/ops/dashboard", "/ops/tasks", "/ops/audit", "/ops/change-requests").hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/ops/tasks/*/toggle", "/ops/change-requests").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/ops/change-requests/*/approve", "/ops/change-requests/*/reject", "/ops/change-requests/*/apply").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(OpsSecurityProperties properties) {
        UserDetails viewer = user(properties.getViewer(), "VIEWER");
        UserDetails operator = user(properties.getOperator(), "OPERATOR");
        UserDetails admin = user(properties.getAdmin(), "ADMIN");
        return new InMemoryUserDetailsManager(viewer, operator, admin);
    }

    private UserDetails user(OpsSecurityProperties.User u, String role) {
        return User.withUsername(u.getUsername())
                .password(u.getPassword())
                .roles(role)
                .build();
    }
}
