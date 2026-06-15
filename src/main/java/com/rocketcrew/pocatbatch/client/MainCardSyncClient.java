package com.rocketcrew.pocatbatch.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainCardSyncClient {

    private final RestTemplate restTemplate;

    @Value("${pocat.main-app.base-url}")
    private String baseUrl;

    @Value("${pocat.main-app.internal-token}")
    private String internalToken;

    /**
     * 카드 동기화 트리거 요청
     * POST {baseUrl}/internal/cards/sync
     * 202 -> 정상 처리
     * 409 (CARD_SYNC_IN_PROGRESS) -> 로그 후 정상 스킵
     * 401 -> RuntimeException
     * 그 외 4xx -> 로그 후 정상 스킵
     */
    public void triggerSync(long jobExecutionId) {
        String url = String.format("%s/internal/cards/sync", baseUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            log.info("카드 동기화 트리거 요청 성공: jobExecutionId={}", jobExecutionId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.error("카드 동기화 트리거 401 오류: jobExecutionId={}", jobExecutionId);
                throw new RuntimeException("카드 동기화 트리거 인증 실패", e);
            }

            String responseBody = e.getResponseBodyAsString();
            if (e.getStatusCode() == HttpStatus.CONFLICT && responseBody.contains("CARD_SYNC_IN_PROGRESS")) {
                log.info("카드 동기화가 이미 진행 중이어서 스킵합니다: jobExecutionId={}", jobExecutionId);
                return;
            }

            log.warn("카드 동기화 트리거 4xx 오류로 스킵: jobExecutionId={}, status={}, body={}",
                    jobExecutionId, e.getStatusCode(), responseBody);
        }
    }
}
