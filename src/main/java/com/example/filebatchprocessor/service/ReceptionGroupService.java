package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.manifest.ParsedManifest;
import com.example.filebatchprocessor.manifest.ParsedManifest.ExpectedFile;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.model.ReceptionGroupMember;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupMemberRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清单驱动入库:到达组服务。负责按清单建组、登记预期成员,并幂等地绑定已到达的文件。
 */
@Service
public class ReceptionGroupService {

    private static final Logger log = LoggerFactory.getLogger(ReceptionGroupService.class);

    private final ReceptionGroupRepository groupRepo;
    private final ReceptionGroupMemberRepository memberRepo;
    private final FileReceptionQueueRepository queueRepo;

    @Value("${batch.file.reception.group.ttl-minutes:360}")
    private long ttlMinutes;

    public ReceptionGroupService(
            ReceptionGroupRepository groupRepo,
            ReceptionGroupMemberRepository memberRepo,
            FileReceptionQueueRepository queueRepo) {
        this.groupRepo = groupRepo;
        this.memberRepo = memberRepo;
        this.queueRepo = queueRepo;
    }

    /**
     * 按解析后的清单建组并登记预期成员;若同 manifestId 的组已存在则幂等返回,不重建。
     */
    @Transactional
    public ReceptionGroup registerFromManifest(ParsedManifest m) {
        Optional<ReceptionGroup> existing = groupRepo.findByManifestId(m.manifestId());
        if (existing.isPresent()) {
            log.info("到达组已存在,幂等返回 manifestId={}", m.manifestId());
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();

        ReceptionGroup group = new ReceptionGroup();
        group.setManifestId(m.manifestId());
        group.setSourceSystem(m.sourceSystem());
        group.setBizDate(m.bizDate());
        group.setStatus("WAITING_FILES");
        group.setTotalMembers(m.files().size());
        group.setArrivedMembers(0);
        group.setDeadline(now.plusMinutes(ttlMinutes));
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        group = groupRepo.save(group);

        for (ExpectedFile f : m.files()) {
            ReceptionGroupMember member = new ReceptionGroupMember();
            member.setGroupId(group.getId());
            member.setExpectedFileName(f.fileName());
            member.setExpectedRecordCount(f.expectedRecordCount());
            member.setExpectedChecksum(f.checksum());
            member.setChecksumAlgorithm(f.checksumAlgorithm());
            member.setRequired(f.required());
            member.setCreatedAt(now);
            memberRepo.save(member);
        }

        // 回扫已到达但尚未绑定的同名队列行
        for (ExpectedFile f : m.files()) {
            Optional<FileReceptionQueue> arrived = queueRepo.findByFileName(f.fileName());
            if (arrived.isPresent()) {
                bindArrivedFile(group.getId(), arrived.get());
            }
        }

        log.info(
                "创建到达组 manifestId={} groupId={} totalMembers={}",
                m.manifestId(),
                group.getId(),
                group.getTotalMembers());
        return group;
    }

    /**
     * 数据文件到达时,尝试将其绑定到正在等待该文件的到达组(manifest 先到、数据文件后到的场景)。
     *
     * <p>按文件名找尚未绑定的同名期望成员,取其所属组;若组存在且仍处于 WAITING_FILES,则绑定。
     * 一个数据文件至多绑定一个等待组(绑定第一个匹配的即返回),避免一文件绑多组。
     */
    @Transactional
    public void tryBindArrivedDataFile(FileReceptionQueue row) {
        List<ReceptionGroupMember> members =
                memberRepo.findByExpectedFileNameAndActualQueueIdIsNull(row.getFileName());
        for (ReceptionGroupMember member : members) {
            Optional<ReceptionGroup> groupOpt = groupRepo.findById(member.getGroupId());
            if (groupOpt.isPresent() && "WAITING_FILES".equals(groupOpt.get().getStatus())) {
                bindArrivedFile(groupOpt.get().getId(), row);
                return;
            }
        }
    }

    /**
     * 将一条已到达的队列行绑定到对应的预期成员上,并推进组的到达计数。
     */
    @Transactional
    public void bindArrivedFile(Long groupId, FileReceptionQueue row) {
        Optional<ReceptionGroupMember> memberOpt =
                memberRepo.findByGroupIdAndExpectedFileName(groupId, row.getFileName());
        if (memberOpt.isEmpty()) {
            log.debug(
                    "无匹配预期成员,跳过绑定 groupId={} fileName={}", groupId, row.getFileName());
            return;
        }

        ReceptionGroupMember member = memberOpt.get();
        // L1:仅首次绑定(原 actualQueueId 为 null)才推进到达计数,避免重复绑同一成员重复计数。
        boolean firstBind = member.getActualQueueId() == null;
        member.setActualQueueId(row.getId());
        // actualRecordCount 暂留 null,对账阶段再计算
        memberRepo.save(member);

        row.setReceptionGroupId(groupId);
        queueRepo.save(row);

        ReceptionGroup group =
                groupRepo
                        .findById(groupId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "到达组不存在 groupId=" + groupId));
        if (firstBind) {
            group.setArrivedMembers(group.getArrivedMembers() + 1);
            groupRepo.save(group);
        }

        log.info(
                "绑定到达文件 groupId={} fileName={} queueId={} arrivedMembers={}",
                groupId,
                row.getFileName(),
                row.getId(),
                group.getArrivedMembers());
    }
}
