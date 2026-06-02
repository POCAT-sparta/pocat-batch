package com.rocketcrew.pocatbatch.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainAppRefundClient {

    private final RestTemplate restTemplate;

    @Value("${pocat.main-app.base-url}")
    private String baseUrl;

    @Value("${pocat.main-app.internal-token}")
    private String internalToken;

    /**
     * 환불 재시도 요청
     * POST {baseUrl}/internal/refunds/{id}/retry
     * 최대 3회 재시도 (지수 백오프)
     * 4xx 오류는 SkipException 발생
     */
    public void retryRefund(Long refundId, Long jobExecutionId) {
        String url = String.format("%s/internal/refunds/%d/retry", baseUrl, refundId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set("Idempotency-Key", String.format("refund-%d-%d", refundId, jobExecutionId));

        HttpEntity<String> request = new HttpEntity<>(headers);

        int maxRetries = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                log.info("환불 재시도 성공: refundId={}", refundId);
                return;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("환불 재시도 4xx 오류: refundId={}, status={}", refundId, e.getStatusCode());
                    throw new RuntimeException("4xx 오류로 스킵", e);
                }
                // 5xx 오류는 재시도
                if (attempt == maxRetries) {
                    log.error("환불 재시도 최대 재시도 초과: refundId={}", refundId, e);
                    throw e;
                }
                try {
                    Thread.sleep(delayMs);
                    delayMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("환불 재시도 실패: refundId={}", refundId, e);
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
    }
}
