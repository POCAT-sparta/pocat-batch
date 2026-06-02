package com.rocketcrew.pocatbatch.domain.auction.service;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocatbatch.domain.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBatchService {

    private final AuctionRepository auctionRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public void activateAuction(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now;
        LocalDateTime endedAt = now.plusHours(7);

        auction.activate(startedAt, endedAt);
        auctionRepository.save(auction);

        String payload = String.format(
            "{\"auctionId\":%d,\"status\":\"ACTIVE\",\"startedAt\":\"%s\",\"endedAt\":\"%s\"}",
            auction.getId(), startedAt, endedAt
        );
        outboxEventWriter.write("auction", String.valueOf(auction.getId()), "auction.activated", payload);
        log.info("경매 활성화 처리: auctionId={}", auction.getId());
    }

    @Transactional
    public void endAuction(Auction auction) {
        auction.end();
        auctionRepository.save(auction);

        String payload = String.format(
            "{\"auctionId\":%d,\"status\":\"ENDED\",\"endedAt\":\"%s\"}",
            auction.getId(), auction.getEndedAt()
        );
        outboxEventWriter.write("auction", String.valueOf(auction.getId()), "auction.ended", payload);
        log.info("경매 종료 처리: auctionId={}", auction.getId());
    }
}
