package com.rocketcrew.pocatbatch.client;

import com.rocketcrew.pocatbatch.client.dto.ApiResponseEnvelope;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class MainAuctionLifecycleClient {

    private final RestTemplate restTemplate;

    @Value("${pocat.main-app.base-url}")
    private String baseUrl;

    @Value("${pocat.main-app.internal-token}")
    private String internalToken;

    /**
     * 경매 활성화 요청
     * POST {baseUrl}/internal/auctions/{auctionId}/activate
     * 최대 3회 재시도 (지수 백오프)
     * 4xx 오류는 즉시 RuntimeException 발생
     */
    public boolean activate(Long auctionId, long jobExecutionId) {
        String url = String.format("%s/internal/auctions/%d/activate", baseUrl, auctionId);
        String idempotencyKey = String.format("auction-activate-%d-%d", auctionId, jobExecutionId);
        return call(url, idempotencyKey, auctionId);
    }

    /**
     * 경매 만료 종료 요청
     * POST {baseUrl}/internal/auctions/{auctionId}/close-expired
     * 최대 3회 재시도 (지수 백오프)
     * 4xx 오류는 즉시 RuntimeException 발생
     */
    public boolean closeExpired(Long auctionId, long jobExecutionId) {
        String url = String.format("%s/internal/auctions/%d/close-expired", baseUrl, auctionId);
        String idempotencyKey = String.format("auction-close-%d-%d", auctionId, jobExecutionId);
        return call(url, idempotencyKey, auctionId);
    }

    private boolean call(String url, String idempotencyKey, Long auctionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        int maxRetries = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<ApiResponseEnvelope<Boolean>> responseEntity = restTemplate.exchange(
                        url, HttpMethod.POST, request,
                        new ParameterizedTypeReference<ApiResponseEnvelope<Boolean>>() {});
                ApiResponseEnvelope<Boolean> body = responseEntity.getBody();
                if (body == null) {
                    throw new IllegalStateException("경매 라이프사이클 응답 본문이 null입니다: auctionId=" + auctionId);
                }
                if (!body.success()) {
                    throw new IllegalStateException("경매 라이프사이클 요청이 실패했습니다: status=" + body.status() + ", auctionId=" + auctionId);
                }
                if (body.data() == null) {
                    throw new IllegalStateException("경매 라이프사이클 응답 데이터가 null입니다: auctionId=" + auctionId);
                }
                return body.data();
            } catch (HttpClientErrorException e) {
                log.warn("경매 라이프사이클 4xx 오류: auctionId={}, status={}", auctionId, e.getStatusCode());
                throw new RuntimeException("4xx 오류로 스킵", e);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("경매 라이프사이클 요청 실패: auctionId={}", auctionId, e);
                    throw e instanceof RuntimeException re ? re : new RuntimeException(e);
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
