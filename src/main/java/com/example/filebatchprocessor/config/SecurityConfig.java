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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OpsSecurityProperties properties)
            throws Exception {
        if (!properties.isEnabled()) {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/prometheus")
                        .hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/ops/console",
                                "/ops/dashboard",
                                "/ops/scheduler",
                                "/ops/dag",
                                "/ops/tasks",
                                "/ops/task-audit",
                                "/ops/audit",
                                "/ops/change-requests")
                        .hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/ops/tasks/*/toggle",
                                "/ops/change-requests",
                                "/ops/scheduler/trigger/*")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(
                                "/ops/change-requests/*/approve",
                                "/ops/change-requests/*/reject",
                                "/ops/change-requests/*/apply")
                        .hasRole("ADMIN")
                        // 迁移运维端点:只读状态查询放给 VIEWER+,破坏性 POST(backfill/switch/deprecate)仅 ADMIN
                        .requestMatchers(HttpMethod.GET, "/ops/migration/**")
                        .hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/ops/migration/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            OpsSecurityProperties properties, org.springframework.core.env.Environment environment) {
        boolean prod = BatchProfiles.isProductionLike(environment);
        UserDetails viewer = user(properties.getViewer(), "VIEWER", prod);
        UserDetails operator = user(properties.getOperator(), "OPERATOR", prod);
        UserDetails admin = user(properties.getAdmin(), "ADMIN", prod);
        return new InMemoryUserDetailsManager(viewer, operator, admin);
    }

    private UserDetails user(OpsSecurityProperties.User u, String role, boolean prod) {
        String password = u.getPassword();
        boolean weak = SecurityCredentials.isWeak(password);
        if (weak) {
            // 生产:拒绝启动,杜绝占位/弱口令进生产;非生产:仅告警,不阻断本地/测试。
            String msg = "ops.security 用户 [" + u.getUsername()
                    + "] 口令为空、命中占位符前缀,或明文长度不足("
                    + "生产请用环境变量配置强口令,支持 {bcrypt}/{noop} 前缀)";
            if (prod) {
                throw new IllegalStateException(msg);
            }
            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).warn("[弱口令] {}", msg);
        }
        return User.withUsername(u.getUsername()).password(password).roles(role).build();
    }
}
