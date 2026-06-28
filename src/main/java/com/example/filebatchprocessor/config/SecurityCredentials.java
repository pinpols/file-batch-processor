package com.example.filebatchprocessor.config;

import java.util.Set;

/** 弱口令判定:剥离编码器前缀 → 归一化 → 占位符前缀集 + 明文最小长度。 */
public final class SecurityCredentials {

    private SecurityCredentials() {}

    private static final int MIN_PLAINTEXT_LENGTH = 16;

    // 归一化(去非字母数字 + 小写)后,命中任一前缀即判占位/弱口令。
    private static final Set<String> PLACEHOLDER_TOKENS = Set.of(
            "changeme", "change", "placeholder", "todo", "secret", "yoursecret",
            "replacewithstrong", "yoursecure", "yoursecurepassword", "example", "password");

    public static boolean isWeak(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        // 剥离 Spring 编码器前缀 {noop}/{bcrypt}/{pbkdf2}...
        String stripped = raw;
        boolean plaintext = true;
        if (raw.startsWith("{")) {
            int end = raw.indexOf('}');
            if (end > 0) {
                String scheme = raw.substring(1, end);
                stripped = raw.substring(end + 1);
                plaintext = "noop".equalsIgnoreCase(scheme);
            }
        }
        String normalized = stripped.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (String token : PLACEHOLDER_TOKENS) {
            if (normalized.startsWith(token) || normalized.contains(token)) {
                return true;
            }
        }
        // 明文(noop 或无前缀)且实际口令过短 → 弱;编码哈希(bcrypt 等)不做长度判定。
        if (plaintext && stripped.length() < MIN_PLAINTEXT_LENGTH) {
            return true;
        }
        return false;
    }
}
