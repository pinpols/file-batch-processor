package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.model.ReceptionGroupMember;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupMemberRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import com.example.filebatchprocessor.service.ManifestReconcileService.ReconcileResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 清单驱动入库:到达组到齐判定 + 对账 + 触发/告警。
 *
 * <p>对单个到达组 {@code evaluate}:
 *
 * <ol>
 *   <li>必填成员未全部到达:已过截止时间则置 EXPIRED 并告警 GROUP_INCOMPLETE,否则保持等待。
 *   <li>必填成员全部到达:逐成员对账并回填 reconcile_status;全通过则置 DISPATCHED 并触发导入,
 *       任一失败则置 FAILED 并告警 GROUP_RECONCILE_FAIL。
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceptionGroupCompletionService {

    private final ReceptionGroupRepository groupRepo;
    private final ReceptionGroupMemberRepository memberRepo;
    private final FileReceptionQueueRepository queueRepo;
    private final ManifestReconcileService reconcileService;
    private final FileAlertService fileAlertService;
    private final ReceptionImportTrigger importTrigger;

    public void evaluate(Long groupId) {
        ReceptionGroup group = groupRepo.findById(groupId).orElse(null);
        if (group == null || !"WAITING_FILES".equals(group.getStatus())) {
            return;
        }

        List<ReceptionGroupMember> members = memberRepo.findByGroupId(groupId);
        List<ReceptionGroupMember> requiredMembers =
                members.stream().filter(ReceptionGroupMember::isRequired).toList();

        boolean allRequiredArrived =
                requiredMembers.stream().allMatch(m -> m.getActualQueueId() != null);

        if (!allRequiredArrived) {
            if (group.getDeadline() != null && LocalDateTime.now().isAfter(group.getDeadline())) {
                group.setStatus("EXPIRED");
                groupRepo.save(group);
                fileAlertService.createAlert(
                        "GROUP_INCOMPLETE",
                        "GROUP_INCOMPLETE",
                        "CRITICAL",
                        "到达组超时未齐: " + group.getManifestId(),
                        null,
                        group.getSourceSystem(),
                        group.getBizDate(),
                        null,
                        Map.of(
                                "manifestId", group.getManifestId(),
                                "arrived", group.getArrivedMembers(),
                                "total", group.getTotalMembers()));
            }
            // 未过截止时间则保持 WAITING_FILES
            return;
        }

        // 全部必填成员到达 -> 逐成员对账
        List<String> allMismatches = new ArrayList<>();
        boolean allPass = true;
        for (ReceptionGroupMember member : members) {
            if (member.getActualQueueId() == null) {
                continue;
            }
            FileReceptionQueue queueRow =
                    queueRepo.findById(member.getActualQueueId()).orElse(null);
            ReconcileResult result = reconcileService.reconcile(member, queueRow);
            member.setReconcileStatus(result.pass() ? "PASS" : "FAIL");
            memberRepo.save(member);
            if (!result.pass()) {
                allPass = false;
                allMismatches.addAll(result.mismatches());
            }
        }

        if (allPass) {
            group.setStatus("DISPATCHED");
            groupRepo.save(group);
            for (ReceptionGroupMember member : members) {
                if (member.getActualQueueId() != null) {
                    importTrigger.triggerImport(member.getActualQueueId());
                }
            }
        } else {
            group.setStatus("FAILED");
            groupRepo.save(group);
            fileAlertService.createAlert(
                    "GROUP_RECONCILE_FAIL",
                    "GROUP_RECONCILE_FAIL",
                    "CRITICAL",
                    "对账失败: " + group.getManifestId(),
                    null,
                    group.getSourceSystem(),
                    group.getBizDate(),
                    null,
                    Map.of(
                            "manifestId", group.getManifestId(),
                            "mismatches", allMismatches));
        }
    }
}
