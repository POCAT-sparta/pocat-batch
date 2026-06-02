package com.rocketcrew.pocatbatch.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    public static final Set<String> FINANCIAL_TOPICS = Set.of("payment", "refund", "settlement");

    /**
     * 기본 KafkaTemplate (acks=1)
     * 일반 이벤트 발행용
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put("acks", "1");
        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }

    /**
     * 금융 관련 KafkaTemplate (acks=all, enable.idempotence=true)
     * 환불, 결제, 정산 이벤트 발행용 (중복 방지 + 높은 안정성)
     */
    @Bean
    public KafkaTemplate<String, String> paymentKafkaTemplate() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put("acks", "all");
        props.put("enable.idempotence", true);
        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}
