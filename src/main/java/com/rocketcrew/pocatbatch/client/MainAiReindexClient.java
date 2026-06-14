package com.rocketcrew.pocatbatch.client;

import com.rocketcrew.pocatbatch.client.dto.ApiResponseEnvelope;
import com.rocketcrew.pocatbatch.client.dto.ReindexChunkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainAiReindexClient {

    private final RestTemplate restTemplate;

    @Value("${pocat.main-app.base-url}")
    private String baseUrl;

    @Value("${pocat.main-app.internal-token}")
    private String internalToken;

    /**
     * AI 카드 임베딩 리인덱스 요청
     * POST {baseUrl}/internal/ai/reindex-cards
     * 최대 3회 재시도 (지수 백오프)
     * 4xx 오류는 즉시 RuntimeException 발생 (스킵)
     */
    public ReindexChunkResponse reindexChunk(List<Long> cardIds, long jobExecutionId) {
        String url = String.format("%s/internal/ai/reindex-cards", baseUrl);

        long firstCardId = cardIds.get(0);
        long lastCardId = cardIds.get(cardIds.size() - 1);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set("Idempotency-Key", String.format("reindex-cards-%d-%d-%d", firstCardId, lastCardId, jobExecutionId));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        HttpEntity<List<Long>> request = new HttpEntity<>(cardIds, headers);

        int maxRetries = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<ApiResponseEnvelope<ReindexChunkResponse>> responseEntity = restTemplate.exchange(
                        url, HttpMethod.POST, request,
                        new ParameterizedTypeReference<ApiResponseEnvelope<ReindexChunkResponse>>() {});
                ApiResponseEnvelope<ReindexChunkResponse> body = responseEntity.getBody();
                if (body == null) {
                    throw new IllegalStateException("AI 카드 리인덱스 응답 본문이 null입니다: cardIds=" + cardIds);
                }
                if (!body.success()) {
                    throw new IllegalStateException("AI 카드 리인덱스 요청이 실패했습니다: status=" + body.status() + ", cardIds=" + cardIds);
                }
                if (body.data() == null) {
                    throw new IllegalStateException("AI 카드 리인덱스 응답 데이터가 null입니다: cardIds=" + cardIds);
                }
                log.info("AI 카드 리인덱스 요청 성공: cardIds={}, response={}", cardIds, body.data());
                return body.data();
            } catch (HttpClientErrorException e) {
                log.warn("AI 카드 리인덱스 4xx 오류: cardIds={}, status={}", cardIds, e.getStatusCode());
                throw new RuntimeException("4xx 오류로 스킵", e);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("AI 카드 리인덱스 요청 실패: cardIds={}", cardIds, e);
                    throw e;
                }
                try {
                    Thread.sleep(delayMs);
                    delayMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }

        throw new IllegalStateException("재시도 루프를 빠져나올 수 없습니다");
    }
}
