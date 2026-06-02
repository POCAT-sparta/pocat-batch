package com.rocketcrew.pocatbatch.domain.ai.entity;

import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * AI 채팅 세션 엔티티.
 * 사용자별 멀티턴 대화 세션 관리.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "ai_chat_sessions",
        indexes = {
                @Index(name = "idx_ai_chat_session_user_id", columnList = "user_id"),
                @Index(name = "idx_ai_chat_session_uuid", columnList = "session_uuid"),
                @Index(name = "idx_ai_chat_session_last_active", columnList = "last_active_at")
        }
)
@SQLDelete(sql = "UPDATE ai_chat_sessions SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AiChatSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_uuid", nullable = false, length = 36, unique = true)
    private String sessionUuid;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    /**
     * 토큰 추가.
     */
    public void addTokens(int tokens) {
        this.totalTokens += tokens;
    }

    /**
     * 마지막 활동 시간 업데이트.
     */
    public void updateLastActiveAt(LocalDateTime now) {
        this.lastActiveAt = now;
    }

    /**
     * 만료된 세션 재활성화.
     */
    public void reactivate(LocalDateTime now) {
        this.isExpired = false;
        this.lastActiveAt = now;
    }
}
