package com.rocketcrew.pocatbatch.domain.auction.service;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.ranking.repository.AuctionBidRepository;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocatbatch.domain.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBatchService {

    private final AuctionRepository auctionRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final AuctionBidRepository auctionBidRepository;

    @Transactional
    public void activateAuction(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now;
        LocalDateTime endedAt = now.plusHours(7);

        auction.activate(startedAt, endedAt);
        auctionRepository.save(auction);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "auction.activated");
        payload.put("auctionId", auction.getId());
        payload.put("sellerId", auction.getSellerId());
        payload.put("startedAt", startedAt.toString());
        payload.put("endedAt", endedAt.toString());

        outboxEventWriter.write("auction", String.valueOf(auction.getId()), "auction.activated", payload);
        log.info("경매 활성화 처리: auctionId={}", auction.getId());
    }

    @Transactional
    public void endAuction(Auction auction) {
        auction.end();
        auctionRepository.save(auction);

        Long winnerId = auction.getHighestBidderId();
        List<Long> loserIds = winnerId != null
                ? auctionBidRepository.findLoserIdsByAuctionIdExcluding(auction.getId(), winnerId)
                : auctionBidRepository.findAllBidderIdsByAuctionId(auction.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "auction.ended");
        payload.put("auctionId", auction.getId());
        payload.put("cardId", auction.getCardId());
        payload.put("sellerId", auction.getSellerId());
        payload.put("winnerId", winnerId);
        payload.put("finalPrice", auction.getHighestPrice());
        payload.put("endedAt", auction.getEndedAt().toString());
        payload.put("loserIds", loserIds);

        outboxEventWriter.write("auction", String.valueOf(auction.getId()), "auction.ended", payload);
        log.info("경매 종료 처리: auctionId={}", auction.getId());
    }
}
