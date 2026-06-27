package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroupMember;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 清单驱动入库:对账服务。
 *
 * <p>对账三要素:文件是否到达、实际数据条数是否等于期望、文件 MD5 是否等于期望。
 * 实际记录数口径与 {@code ReconcileJobConfig.countDataLines} 一致:UTF-8 读取、跳过首行表头、
 * 仅计非空白行。
 */
@Service
public class ManifestReconcileService {

    /** 对账结果:是否通过 + 不一致项明细。 */
    public static record ReconcileResult(boolean pass, List<String> mismatches) {}

    /**
     * 对账单个到达组成员。
     *
     * @param member 期望(条数/校验和)及回填字段载体
     * @param queueRow 实际到达的文件队列行;为 null 表示文件未到达
     * @return 对账结果
     */
    public ReconcileResult reconcile(ReceptionGroupMember member, FileReceptionQueue queueRow) {
        List<String> mismatches = new ArrayList<>();

        // 文件存在性:未到达则直接返回,不再做后续检查
        if (queueRow == null) {
            mismatches.add("file not arrived");
            return new ReconcileResult(false, mismatches);
        }

        // 条数对账
        if (member.getExpectedRecordCount() != null) {
            long actual = countDataLines(queueRow.getFilePath());
            member.setActualRecordCount(actual);
            long expected = member.getExpectedRecordCount();
            if (actual != expected) {
                mismatches.add("record count: expected=" + expected + " actual=" + actual);
            }
        }

        // 校验和对账(接收链路已算 MD5 存 fileHash,直接比)
        String expectedChecksum = member.getExpectedChecksum();
        if (expectedChecksum != null && !expectedChecksum.isBlank()) {
            if (!expectedChecksum.equalsIgnoreCase(queueRow.getFileHash())) {
                mismatches.add("checksum mismatch");
            }
        }

        return new ReconcileResult(mismatches.isEmpty(), mismatches);
    }

    /**
     * 统计文件数据行数:UTF-8 读取,跳过首行表头,仅计非空白行;空文件返回 0。
     * 口径同 {@code ReconcileJobConfig.countDataLines}。
     */
    private long countDataLines(String filePath) {
        long count = 0L;
        try (BufferedReader br = Files.newBufferedReader(Path.of(filePath), StandardCharsets.UTF_8)) {
            boolean first = true;
            String line;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (!line.isBlank()) {
                    count++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取对账文件: " + filePath, e);
        }
        return count;
    }
}
