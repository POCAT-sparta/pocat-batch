package com.rocketcrew.pocatbatch.client.dto;

import java.util.List;

/**
 * 메인 백엔드 internal API {@code POST /internal/ai/reindex-cards} 요청 본문 매핑용 DTO
 */
public record ReindexChunkRequest(List<Long> cardIds) {
}
