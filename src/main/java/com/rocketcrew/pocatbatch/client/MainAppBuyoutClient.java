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
public class MainAppBuyoutClient {

    private final RestTemplate restTemplate;

    @Value("${pocat.main-app.base-url}")
    private String baseUrl;

    @Value("${pocat.main-app.internal-token}")
    private String internalToken;

    /**
     * 경매 구매 확정 실패 복구 요청
     * POST {baseUrl}/internal/auctions/{id}/recover-buyout
     * 최대 3회 재시도 (지수 백오프)
     * 4xx 오류는 SkipException 발생
     */
    public void recoverBuyout(Long auctionId, Long jobExecutionId) {
        String url = String.format("%s/internal/auctions/%d/recover-buyout", baseUrl, auctionId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set("Idempotency-Key", String.format("buyout-%d-%d", auctionId, jobExecutionId));

        HttpEntity<String> request = new HttpEntity<>(headers);

        int maxRetries = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                log.info("경매 구매 확정 복구 성공: auctionId={}", auctionId);
                return;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("경매 구매 확정 복구 4xx 오류: auctionId={}, status={}", auctionId, e.getStatusCode());
                    throw new RuntimeException("4xx 오류로 스킵", e);
                }
                // 5xx 오류는 재시도
                if (attempt == maxRetries) {
                    log.error("경매 구매 확정 복구 최대 재시도 초과: auctionId={}", auctionId, e);
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
                    log.error("경매 구매 확정 복구 실패: auctionId={}", auctionId, e);
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
