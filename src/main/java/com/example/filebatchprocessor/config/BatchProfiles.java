package com.example.filebatchprocessor.config;

import java.util.Set;
import org.springframework.core.env.Environment;

/**
 * Profile 判定 fail-secure:空/未知 profile 一律当生产,避免"漏开 prod profile 就静默放行"。
 * 仅当所有 active profile 都明确属于非生产集时才判为非生产。
 */
public final class BatchProfiles {

    private BatchProfiles() {}

    /** 明确的非生产 profile(其余一切——含空、staging、uat、preprod、未知——都当生产)。 */
    private static final Set<String> NON_PROD = Set.of("dev", "local", "test", "h2", "it", "integration");

    public static boolean isProductionLike(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active == null || active.length == 0) {
            return true; // 空 profile = 当生产(fail-secure)
        }
        for (String p : active) {
            if (!NON_PROD.contains(p.trim().toLowerCase())) {
                return true; // 任一非"已知非生产" profile(prod/staging/uat/qa/未知…)→ 当生产
            }
        }
        return false; // 全部是已知非生产 profile
    }
}
