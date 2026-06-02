package com.rocketcrew.pocatbatch.domain.outbox.service;

import com.rocketcrew.pocatbatch.config.KafkaProducerConfig;
import com.rocketcrew.pocatbatch.domain.outbox.entity.OutboxEvent;
import com.rocketcrew.pocatbatch.domain.outbox.enums.OutboxStatus;
import com.rocketcrew.pocatbatch.domain.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;

    /**
     * REQUIRES_NEW를 통해 건당 독립적인 트랜잭션을 보장합니다.
     * 하나의 이벤트가 실패하거나 롤백되어도 다른 이벤트에 영향을 주지 않습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEvent event, KafkaTemplate<String, String> template) {

        // 1. 조건부 UPDATE로 선점 (Atomic)
        int updated = outboxRepository.markProcessingIfPending(
                event.getId(), OutboxStatus.PENDING, OutboxStatus.PROCESSING
        );

        if (updated == 0) return; // 이미 다른 서버가 선점

        // relay()에 @Transactional이 없어 findTop100... 이후 엔티티가 detached 상태임.
        // REQUIRES_NEW 트랜잭션 안에서 재조회해야 dirty checking이 정상 동작함.
        OutboxEvent managed = outboxRepository.findById(event.getId()).orElse(null);
        if (managed == null) return;

        try {
            // 3. Kafka 발행 (동기 대기)
            template.send(managed.getTopic(), managed.getPartitionKey(), managed.getPayload())
                    .get(5, TimeUnit.SECONDS);

            // 4. 발행 성공 ➡️ SENT
            managed.markSent();
            log.info("릴레이 성공: id={}, topic={}", managed.getId(), managed.getTopic());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("릴레이 인터럽트 발생: id={}", managed.getId());
            managed.markPendingForRetry();
        } catch (Exception e) {
            managed.markPendingForRetry();
            log.error("릴레이 실패: id={}, retryCount={}", managed.getId(), managed.getRetryCount(), e);
        }
        // managed 엔티티이므로 트랜잭션 커밋 시 dirty checking으로 자동 UPDATE됨.
    }
}
