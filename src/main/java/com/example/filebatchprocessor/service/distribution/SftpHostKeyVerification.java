package com.example.filebatchprocessor.service.distribution;

import java.io.File;
import java.io.IOException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

/**
 * SFTP 主机密钥校验策略(替代此前到处硬写的 PromiscuousVerifier)。
 *
 * <p>安全默认 = fail-closed:
 *
 * <ul>
 *   <li>{@code insecureSkip=true}:显式跳过校验(仅供本地/测试,生产禁用);
 *   <li>配置了 {@code knownHostsPath}:用该 known_hosts 文件校验;
 *   <li>都没配:加载默认 known_hosts(~/.ssh/known_hosts 等),缺失则抛异常拒绝连接。
 * </ul>
 */
public final class SftpHostKeyVerification {

    private SftpHostKeyVerification() {}

    public static void apply(SSHClient client, String knownHostsPath, boolean insecureSkip) throws IOException {
        if (insecureSkip) {
            client.addHostKeyVerifier(new PromiscuousVerifier());
            return;
        }
        if (knownHostsPath != null && !knownHostsPath.isBlank()) {
            client.loadKnownHosts(new File(knownHostsPath));
            return;
        }
        // fail-closed:加载默认 known_hosts;若找不到则 sshj 抛 IOException,连接被拒绝。
        client.loadKnownHosts();
    }
}
