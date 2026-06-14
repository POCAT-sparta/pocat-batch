package com.rocketcrew.pocatbatch.client.dto;

/**
 * 메인 백엔드 internal API {@code POST /internal/ai/reindex-cards} 응답 매핑용 DTO
 */
public record ReindexChunkResponse(
        int processedCount,
        int skippedCount,
        int indexedCount,
        int failedCount,
        boolean rateLimited
) {
}
