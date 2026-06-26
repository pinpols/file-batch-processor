package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.SchedulerLeaderLock;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SchedulerLeaderLockRepository extends JpaRepository<SchedulerLeaderLock, String> {

    @Modifying
    @Transactional
    @Query(
            value =
                    """
            INSERT INTO scheduler_leader_lock(lock_name, owner_id, expires_at, updated_at)
            VALUES (:lockName, :ownerId, :expiresAt, CURRENT_TIMESTAMP)
            ON CONFLICT (lock_name) DO UPDATE
            SET owner_id = EXCLUDED.owner_id,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            WHERE scheduler_leader_lock.expires_at < CURRENT_TIMESTAMP
               OR scheduler_leader_lock.owner_id = :ownerId
            """,
            nativeQuery = true)
    int tryAcquireOrRenew(
            @Param("lockName") String lockName,
            @Param("ownerId") String ownerId,
            @Param("expiresAt") LocalDateTime expiresAt);
}
