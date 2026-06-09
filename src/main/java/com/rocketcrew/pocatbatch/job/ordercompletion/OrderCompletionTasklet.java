package com.rocketcrew.pocatbatch.job.ordercompletion;

import com.rocketcrew.pocatbatch.domain.order.entity.Order;
import com.rocketcrew.pocatbatch.domain.order.repository.OrderRepository;
import com.rocketcrew.pocatbatch.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocatbatch.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletionTasklet implements Tasklet {

    private static final int COMPLETION_DAYS = 7;
    private static final List<RefundStatus> ACTIVE_REFUND_STATUSES = List.of(
            RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.FAILED_RETRYABLE
    );

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(COMPLETION_DAYS);
        List<Order> candidates = orderRepository.findCompletionEligible(cutoff);

        int completedCount = 0;
        for (Order order : candidates) {
            boolean hasActiveRefund = refundRepository.existsByOrderIdAndStatusIn(
                    order.getId(), ACTIVE_REFUND_STATUSES);
            if (!hasActiveRefund) {
                order.completeOrder();
                orderRepository.save(order);
                completedCount++;
                log.debug("주문 자동 완료 처리: orderId={}, orderUid={}", order.getId(), order.getOrderUid());
            }
        }
        log.info("주문 자동 완료 처리 완료: 대상={}, 완료={}", candidates.size(), completedCount);
        return RepeatStatus.FINISHED;
    }
}
