package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.manifest.ParsedManifest;
import com.example.filebatchprocessor.manifest.ParsedManifest.ExpectedFile;
import com.example.filebatchprocessor.model.FileAlertLog;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.model.ReceptionGroupMember;
import com.example.filebatchprocessor.repository.FileAlertLogRepository;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupMemberRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import com.example.filebatchprocessor.service.ReceptionGroupCompletionService;
import com.example.filebatchprocessor.service.ReceptionGroupService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 清单驱动入库(#3+#6)端到端 IT:建组 -> 绑定到达 -> 到齐判定 + 对账 -> 状态机 + 告警。
 *
 * <p>三场景:齐且对账通过(DISPATCHED 无告警)、缺文件超时(EXPIRED + GROUP_INCOMPLETE)、
 * 条数不符(FAILED + GROUP_RECONCILE_FAIL)。
 *
 * <p>跑这条 IT 会经 Flyway V1_38 建表 + ddl-auto 校验 ReceptionGroup/Member 实体列与表对齐。
 * checksum(MD5)对账路径已在 Task 5 单测覆盖,此处 checksum 传 null,聚焦条数与缺件两条主路径。
 */
@SpringBootTest
@ActiveProfiles("test")
class ManifestDrivenIntakeIT extends PostgresContainerSupport {

    @Autowired
    private ReceptionGroupService receptionGroupService;

    @Autowired
    private ReceptionGroupCompletionService completionService;

    @Autowired
    private ReceptionGroupRepository groupRepo;

    @Autowired
    private ReceptionGroupMemberRepository memberRepo;

    @Autowired
    private FileReceptionQueueRepository queueRepo;

    @Autowired
    private FileAlertLogRepository alertLogRepository;

    /** 写一个 CSV:表头 + dataLines 条数据行(口径同对账 countDataLines:跳过首行表头)。 */
    private FileReceptionQueue stageArrivedFile(Path dir, String fileName, int dataLines) throws IOException {
        Path file = dir.resolve(fileName);
        StringBuilder sb = new StringBuilder("id,name,description\n");
        for (int i = 1; i <= dataLines; i++) {
            sb.append(i).append(",name").append(i).append(",desc").append(i).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);

        FileReceptionQueue row = new FileReceptionQueue();
        row.setFileName(fileName);
        row.setFilePath(file.toString());
        row.setFileSize(Files.size(file));
        row.setStatus("RECEIVED");
        row.setSourceSystem("S");
        return queueRepo.save(row);
    }

    /** 统计某 alertType 当前总行数(GROUP_INCOMPLETE / GROUP_RECONCILE_FAIL)。 */
    private long countAlerts(String alertType) {
        return alertLogRepository.findAll().stream()
                .filter(a -> alertType.equals(a.getAlertType()))
                .count();
    }

    @Test
    void scenarioComplete_allArrivedAndReconcilePass_dispatchedNoAlert(@TempDir Path dir) throws Exception {
        String tag = "c" + UUID.randomUUID().toString().substring(0, 8);
        String fa = tag + "-a.csv";
        String fb = tag + "-b.csv";
        String manifestId = "M-complete-" + tag;
        long incompleteBefore = countAlerts("GROUP_INCOMPLETE");
        long reconcileFailBefore = countAlerts("GROUP_RECONCILE_FAIL");

        ParsedManifest manifest = new ParsedManifest(
                manifestId,
                "S",
                "2026-06-27",
                List.of(
                        new ExpectedFile(fa, 2L, null, "MD5", true),
                        new ExpectedFile(fb, 1L, null, "MD5", true)));
        ReceptionGroup group = receptionGroupService.registerFromManifest(manifest);

        FileReceptionQueue a = stageArrivedFile(dir, fa, 2);
        FileReceptionQueue b = stageArrivedFile(dir, fb, 1);
        receptionGroupService.bindArrivedFile(group.getId(), a);
        receptionGroupService.bindArrivedFile(group.getId(), b);

        completionService.evaluate(group.getId());

        ReceptionGroup reloaded = groupRepo.findById(group.getId()).orElseThrow();
        assertEquals("DISPATCHED", reloaded.getStatus());

        List<ReceptionGroupMember> members = memberRepo.findByGroupId(group.getId());
        assertEquals(2, members.size());
        assertTrue(members.stream().allMatch(m -> "PASS".equals(m.getReconcileStatus())), "两成员均应 PASS");

        assertEquals(incompleteBefore, countAlerts("GROUP_INCOMPLETE"), "成功路径不应产生 GROUP_INCOMPLETE");
        assertEquals(
                reconcileFailBefore, countAlerts("GROUP_RECONCILE_FAIL"), "成功路径不应产生 GROUP_RECONCILE_FAIL");
    }

    @Test
    void scenarioMissingFile_pastDeadline_expiredWithIncompleteAlert(@TempDir Path dir) throws Exception {
        String tag = "m" + UUID.randomUUID().toString().substring(0, 8);
        String fa = tag + "-a.csv";
        String fb = tag + "-b.csv";
        String manifestId = "M-missing-" + tag;
        long incompleteBefore = countAlerts("GROUP_INCOMPLETE");

        ParsedManifest manifest = new ParsedManifest(
                manifestId,
                "S",
                "2026-06-27",
                List.of(
                        new ExpectedFile(fa, 2L, null, "MD5", true),
                        new ExpectedFile(fb, 1L, null, "MD5", true)));
        ReceptionGroup group = receptionGroupService.registerFromManifest(manifest);

        // 只绑定 a.csv,b.csv 缺失
        FileReceptionQueue a = stageArrivedFile(dir, fa, 2);
        receptionGroupService.bindArrivedFile(group.getId(), a);

        // 把截止时间推到过去,触发超时判定
        ReceptionGroup toExpire = groupRepo.findById(group.getId()).orElseThrow();
        toExpire.setDeadline(LocalDateTime.now().minusMinutes(5));
        groupRepo.save(toExpire);

        completionService.evaluate(group.getId());

        ReceptionGroup reloaded = groupRepo.findById(group.getId()).orElseThrow();
        assertEquals("EXPIRED", reloaded.getStatus());
        assertEquals(
                incompleteBefore + 1, countAlerts("GROUP_INCOMPLETE"), "缺文件超时应产生 GROUP_INCOMPLETE");
    }

    @Test
    void scenarioRecordCountMismatch_failedWithReconcileFailAlert(@TempDir Path dir) throws Exception {
        String tag = "n" + UUID.randomUUID().toString().substring(0, 8);
        String fa = tag + "-a.csv";
        String manifestId = "M-count-" + tag;
        long reconcileFailBefore = countAlerts("GROUP_RECONCILE_FAIL");

        ParsedManifest manifest = new ParsedManifest(
                manifestId,
                "S",
                "2026-06-27",
                List.of(new ExpectedFile(fa, 5L, null, "MD5", true)));
        ReceptionGroup group = receptionGroupService.registerFromManifest(manifest);

        // 期望 5 行,真文件只有 2 行 -> 条数不符
        FileReceptionQueue a = stageArrivedFile(dir, fa, 2);
        receptionGroupService.bindArrivedFile(group.getId(), a);

        completionService.evaluate(group.getId());

        ReceptionGroup reloaded = groupRepo.findById(group.getId()).orElseThrow();
        assertEquals("FAILED", reloaded.getStatus());

        ReceptionGroupMember member =
                memberRepo.findByGroupIdAndExpectedFileName(group.getId(), fa).orElseThrow();
        assertEquals("FAIL", member.getReconcileStatus());
        assertEquals(2L, member.getActualRecordCount());

        assertEquals(
                reconcileFailBefore + 1,
                countAlerts("GROUP_RECONCILE_FAIL"),
                "条数不符应产生 GROUP_RECONCILE_FAIL");
    }
}
