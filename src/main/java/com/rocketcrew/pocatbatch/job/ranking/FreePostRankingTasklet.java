package com.rocketcrew.pocatbatch.job.ranking;

import com.rocketcrew.pocatbatch.domain.freepost.entity.FreePost;
import com.rocketcrew.pocatbatch.domain.freepost.repository.FreePostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreePostRankingTasklet implements Tasklet {

    public static final String RANKING_KEY = "ranking:free:popular";
    private static final String RANKING_NEW_KEY = "ranking:free:popular:new";

    private final StringRedisTemplate redisTemplate;
    private final FreePostRepository freePostRepository;

    @Value("${pocat.batch.ranking.free.cache-size:100}")
    private int cacheSize;

    @Value("${pocat.batch.ranking.free.ttl-seconds:70}")
    private int ttlSeconds;

    @Value("${pocat.batch.ranking.free.popular-days:7}")
    private int popularDays;

    @Value("${pocat.batch.ranking.free.comment-weight:3}")
    private int commentWeight;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<FreePost> posts = freePostRepository.findTopByPopularScore(
                PageRequest.of(0, cacheSize),
                LocalDateTime.now().minusDays(popularDays));

        if (posts.isEmpty()) {
            log.info("랭킹 갱신 대상 게시글 없음");
            return RepeatStatus.FINISHED;
        }

        try {
            redisTemplate.delete(RANKING_NEW_KEY);

            for (FreePost post : posts) {
                double score = post.getViewCount() + (double) post.getCommentCount() * commentWeight;
                redisTemplate.opsForZSet().add(RANKING_NEW_KEY, post.getId().toString(), score);
            }

            if (Boolean.TRUE.equals(redisTemplate.hasKey(RANKING_NEW_KEY))) {
                redisTemplate.rename(RANKING_NEW_KEY, RANKING_KEY);
                redisTemplate.expire(RANKING_KEY, ttlSeconds, TimeUnit.SECONDS);
                log.info("자유게시판 랭킹 갱신 완료: {}개", posts.size());
            }
        } finally {
            redisTemplate.delete(RANKING_NEW_KEY);
        }

        return RepeatStatus.FINISHED;
    }
}
