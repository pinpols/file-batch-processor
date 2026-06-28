package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReceptionGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceptionGroupMemberRepository extends JpaRepository<ReceptionGroupMember, Long> {
    List<ReceptionGroupMember> findByGroupId(Long groupId);

    Optional<ReceptionGroupMember> findByGroupIdAndExpectedFileName(Long groupId, String expectedFileName);

    /** 找尚未绑定(actualQueueId 为空)的同名期望成员,用于数据文件后到时回绑等待中的组。 */
    List<ReceptionGroupMember> findByExpectedFileNameAndActualQueueIdIsNull(String expectedFileName);
}
