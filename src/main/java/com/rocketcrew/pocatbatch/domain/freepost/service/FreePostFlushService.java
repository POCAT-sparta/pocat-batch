package com.rocketcrew.pocatbatch.domain.freepost.service;

import com.rocketcrew.pocatbatch.domain.freepost.repository.FreePostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreePostFlushService {

    private final FreePostRepository freePostRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseViewCount(Long postId, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("View count increment must not be negative: " + count);
        }
        int updated = freePostRepository.increaseViewCount(postId, count);
        if (updated == 0) {
            log.warn("increaseViewCount skipped: postId={} not found (deleted), no retry", postId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCommentCount(Long postId, int delta) {
        int updated = freePostRepository.updateCommentCount(postId, delta);
        if (updated == 0) {
            log.warn("updateCommentCount skipped: postId={} not found (deleted), no retry", postId);
        }
    }
}
