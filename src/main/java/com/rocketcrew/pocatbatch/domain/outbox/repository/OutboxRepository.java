package com.rocketcrew.pocatbatch.domain.outbox.repository;

import com.rocketcrew.pocatbatch.domain.outbox.entity.OutboxEvent;
import com.rocketcrew.pocatbatch.domain.outbox.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OutboxStatus status, LocalDateTime before);

    /**
     * 조건부 UPDATE (선점용)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent o SET o.status = :to, o.processedAt = CURRENT_TIMESTAMP " +
            "WHERE o.id = :id AND o.status = :from")
    int markProcessingIfPending(
            @Param("id") Long id,
            @Param("from") OutboxStatus from,
            @Param("to") OutboxStatus to
    );

    /**
     * PROCESSING 상태에서 일정 시간 이상 멈춘 이벤트 조회 (Reaper용, 최대 100건)
     */
    List<OutboxEvent> findTop100ByStatusAndProcessedAtBefore(OutboxStatus status, LocalDateTime before);

    /**
     * 오래된 SENT 이벤트 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :cutoff")
    int deleteOldEvents(
            @Param("status") OutboxStatus status,
            @Param("cutoff") LocalDateTime cutoff
    );
}
