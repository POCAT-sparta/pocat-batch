package com.rocketcrew.pocatbatch.domain.auction.ranking.service;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocatbatch.domain.auction.ranking.config.AuctionRankingProperties;
import com.rocketcrew.pocatbatch.domain.auction.ranking.dto.AuctionCountProjection;
import com.rocketcrew.pocatbatch.domain.auction.ranking.repository.AuctionBidRepository;
import com.rocketcrew.pocatbatch.domain.auction.ranking.repository.LikeRepository;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionRankingService {

    private final StringRedisTemplate redisTemplate;
    private final AuctionRepository auctionRepository;
    private final LikeRepository likeRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionRankingProperties properties;

    static final String RANKING_KEY = "ranking:auction:popular";

    public void refreshRanking() {
        try {
            List<Auction> activeAuctions = auctionRepository.findAllByStatus(AuctionStatus.ACTIVE);
            if (activeAuctions.isEmpty()) {
                redisTemplate.delete(RANKING_KEY);
                log.debug("활성 경매 없음 — 랭킹 캐시 삭제");
                return;
            }

            List<Long> auctionIds = activeAuctions.stream().map(Auction::getId).toList();

            Map<Long, Long> likeCounts = toLongMap(likeRepository.countByAuctionIdIn(auctionIds));
            Map<Long, Long> bidCounts = toLongMap(auctionBidRepository.countByAuctionIdIn(auctionIds));

            String newKey = RANKING_KEY + ":new";
            redisTemplate.delete(newKey);

            for (Auction auction : activeAuctions) {
                long likeCount = likeCounts.getOrDefault(auction.getId(), 0L);
                long bidCount = bidCounts.getOrDefault(auction.getId(), 0L);
                double score = likeCount * properties.getLikeWeight() + bidCount * properties.getBidWeight();
                redisTemplate.opsForZSet().add(newKey, auction.getId().toString(), score);
            }

            trimToCacheSize(newKey);
            redisTemplate.rename(newKey, RANKING_KEY);
            redisTemplate.expire(RANKING_KEY, properties.getTtlSeconds(), TimeUnit.SECONDS);
            log.debug("경매 랭킹 갱신 완료: {} 개 경매", activeAuctions.size());

        } catch (Exception e) {
            log.warn("경매 랭킹 갱신 실패", e);
            throw new RuntimeException("경매 랭킹 갱신 실패", e);
        }
    }

    private void trimToCacheSize(String key) {
        Long size = redisTemplate.opsForZSet().zCard(key);
        if (size != null && size > properties.getCacheSize()) {
            redisTemplate.opsForZSet().removeRange(key, 0, size - properties.getCacheSize() - 1);
        }
    }

    private Map<Long, Long> toLongMap(List<AuctionCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(AuctionCountProjection::getAuctionId, AuctionCountProjection::getCnt));
    }
}
