package com.rocketcrew.pocatbatch.domain.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocatbatch.domain.outbox.entity.OutboxEvent;
import com.rocketcrew.pocatbatch.domain.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 비즈니스 이벤트를 JSON 페이로드로 변환하여 아웃박스 테이블에 PENDING 상태로 저장합니다.
     *
     * @param topic        카프카 토픽명
     * @param partitionKey 카프카 파티션 키
     * @param eventType    이벤트 타입
     * @param payload      JSON 페이로드
     */
    public void write(String topic, String partitionKey, String eventType, String payload) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.pending(topic, partitionKey, eventType, payload);
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("아웃박스 이벤트 저장 중 치명적 에러 발생: " + eventType, e);
        }
    }

    /**
     * 객체를 JSON으로 변환하여 아웃박스에 저장합니다.
     */
    public void write(String topic, String partitionKey, String eventType, Object eventPayload) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패: " + eventType, e);
        }
        write(topic, partitionKey, eventType, payload);
    }
}
