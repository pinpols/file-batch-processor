package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TargetSystemCircuitStateRepository extends JpaRepository<TargetSystemCircuitState, String> {

    Optional<TargetSystemCircuitState> findByTargetSystem(String targetSystem);

    /**
     * 原子地把 OPEN 且已过冷却的熔断器置为 HALF_OPEN(单探测者闸门)。受影响行数=1 表示本调用方抢到唯一探测资格。
     */
    @Modifying
    @Query("UPDATE TargetSystemCircuitState s SET s.status = 'HALF_OPEN', s.updatedAt = :now "
            + "WHERE s.targetSystem = :targetSystem AND s.status = 'OPEN' "
            + "AND s.cooldownUntil IS NOT NULL AND s.cooldownUntil < :now")
    int tryTransitionToHalfOpen(@Param("targetSystem") String targetSystem, @Param("now") LocalDateTime now);
}
