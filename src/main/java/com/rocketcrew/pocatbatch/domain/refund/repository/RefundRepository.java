package com.rocketcrew.pocatbatch.domain.refund.repository;

import com.rocketcrew.pocatbatch.domain.refund.entity.Refund;
import com.rocketcrew.pocatbatch.domain.refund.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * 자동 재시도 대상 조회:
     * 1) FAILED_RETRYABLE 중 nextRetryAt이 지난 것
     * 2) PROCESSING 중 updatedAt이 stuckBefore보다 오래된 것 (approveRefund DB 실패로 방치된 건)
     */
    @Query("SELECT r FROM Refund r WHERE " +
           "(r.status = :retryable AND r.nextRetryAt <= :now) OR " +
           "(r.status = :processing AND r.updatedAt < :stuckBefore)")
    List<Refund> findRetryableTargets(
            @Param("retryable") RefundStatus retryable,
            @Param("now") LocalDateTime now,
            @Param("processing") RefundStatus processing,
            @Param("stuckBefore") LocalDateTime stuckBefore);
}
