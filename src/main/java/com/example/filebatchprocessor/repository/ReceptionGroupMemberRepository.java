package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReceptionGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceptionGroupMemberRepository
        extends JpaRepository<ReceptionGroupMember, Long> {
    List<ReceptionGroupMember> findByGroupId(Long groupId);

    Optional<ReceptionGroupMember> findByGroupIdAndExpectedFileName(
            Long groupId, String expectedFileName);
}
