package com.rocketcrew.pocatbatch.job.refundretry;

import com.rocketcrew.pocatbatch.client.MainAppRefundClient;
import com.rocketcrew.pocatbatch.domain.refund.entity.Refund;
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
public class RefundRetryTasklet implements Tasklet {

    private final RefundRepository refundRepository;
    private final MainAppRefundClient mainAppRefundClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime stuckBefore = now.minusMinutes(5);

            List<Refund> retryableRefunds = refundRepository.findRetryableTargets(
                    RefundStatus.FAILED_RETRYABLE, now,
                    RefundStatus.PROCESSING, stuckBefore
            );

            int retriedCount = 0;
            long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecution().getId();

            for (Refund refund : retryableRefunds) {
                try {
                    mainAppRefundClient.retryRefund(refund.getId(), jobExecutionId);
                    retriedCount++;
                } catch (Exception e) {
                    log.warn("환불 재시도 실패: refundId={}", refund.getId(), e);
                }
            }

            log.info("환불 재시도 완료: {} 개 환불 처리됨", retriedCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("환불 재시도 작업 실패", e);
            throw new RuntimeException("환불 재시도 중 오류 발생", e);
        }
    }
}
