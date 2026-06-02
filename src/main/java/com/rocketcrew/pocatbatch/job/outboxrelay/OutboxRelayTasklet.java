package com.rocketcrew.pocatbatch.job.outboxrelay;

import com.rocketcrew.pocatbatch.config.KafkaProducerConfig;
import com.rocketcrew.pocatbatch.domain.outbox.entity.OutboxEvent;
import com.rocketcrew.pocatbatch.domain.outbox.enums.OutboxStatus;
import com.rocketcrew.pocatbatch.domain.outbox.repository.OutboxRepository;
import com.rocketcrew.pocatbatch.domain.outbox.service.OutboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayTasklet implements Tasklet {

    private final OutboxRepository outboxRepository;
    private final OutboxProcessor outboxProcessor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTemplate<String, String> paymentKafkaTemplate;

    @Value("${outbox.relay.age-threshold-seconds:10}")
    private int ageThresholdSeconds;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            LocalDateTime ageThreshold = LocalDateTime.now().minusSeconds(ageThresholdSeconds);
            List<OutboxEvent> pendingEvents = outboxRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    OutboxStatus.PENDING, ageThreshold
            );

            for (OutboxEvent event : pendingEvents) {
                try {
                    // 금융 관련 토픽은 paymentKafkaTemplate 사용
                    KafkaTemplate<String, String> template = KafkaProducerConfig.FINANCIAL_TOPICS.contains(event.getTopic())
                            ? paymentKafkaTemplate
                            : kafkaTemplate;
                    outboxProcessor.processEvent(event, template);
                } catch (Exception e) {
                    log.error("아웃박스 이벤트 처리 실패: id={}", event.getId(), e);
                }
            }

            log.info("아웃박스 릴레이 완료: {} 개 이벤트 처리", pendingEvents.size());
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("아웃박스 릴레이 실패", e);
            throw new RuntimeException("아웃박스 릴레이 중 오류 발생", e);
        }
    }
}
