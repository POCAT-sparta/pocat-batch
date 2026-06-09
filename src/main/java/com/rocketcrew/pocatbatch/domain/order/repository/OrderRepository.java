package com.rocketcrew.pocatbatch.domain.order.repository;

import com.rocketcrew.pocatbatch.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(value = """
            SELECT o.* FROM orders o
            JOIN payments p ON p.order_id = o.id AND p.deleted_at IS NULL
            WHERE o.status = 'PAYMENT_COMPLETED'
              AND o.deleted_at IS NULL
              AND p.paid_at IS NOT NULL
              AND p.paid_at <= :cutoff
            """, nativeQuery = true)
    List<Order> findCompletionEligible(@Param("cutoff") LocalDateTime cutoff);
}
