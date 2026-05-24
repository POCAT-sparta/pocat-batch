package com.rocketcrew.pocatbatch.domain.freepost.service;

import com.rocketcrew.pocatbatch.domain.freepost.repository.FreePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FreePostFlushService {

    private final FreePostRepository freePostRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseViewCount(Long postId, int count) {
        freePostRepository.increaseViewCount(postId, count);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCommentCount(Long postId, int delta) {
        freePostRepository.updateCommentCount(postId, delta);
    }
}
