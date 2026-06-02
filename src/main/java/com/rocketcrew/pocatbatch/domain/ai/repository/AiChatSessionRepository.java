package com.rocketcrew.pocatbatch.domain.ai.repository;

import com.rocketcrew.pocatbatch.domain.ai.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    /**
     * 지정된 시간 이전의 비활성 세션을 만료 처리.
     *
     * @param threshold 기준 시간
     * @return 만료된 세션 수
     */
    @Modifying
    @Query("UPDATE AiChatSession s SET s.isExpired = true WHERE s.lastActiveAt < :threshold AND s.isExpired = false")
    int expireSessionsBeforeTime(@Param("threshold") LocalDateTime threshold);
}
