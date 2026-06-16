package com.rocketcrew.pocatbatch.job.auctionactivation;

import com.rocketcrew.pocatbatch.client.MainAuctionLifecycleClient;
import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionActivationTaskletTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private MainAuctionLifecycleClient mainAuctionLifecycleClient;

    @InjectMocks
    private AuctionActivationTasklet auctionActivationTasklet;

    private ChunkContext newChunkContext(long jobExecutionId) {
        StepContext stepContext = new StepContext(
                MetaDataInstanceFactory.createStepExecution(
                        MetaDataInstanceFactory.createJobExecution(
                                "auctionActivationJob", jobExecutionId, jobExecutionId
                        ),
                        "auctionActivationStep",
                        1L
                )
        );
        return new ChunkContext(stepContext);
    }

    private Auction mockAuction(long id) {
        Auction auction = mock(Auction.class);
        lenient().when(auction.getId()).thenReturn(id);
        return auction;
    }

    @Test
    void execute_대상_경매가_없으면_즉시_종료하고_클라이언트를_호출하지_않는다() throws Exception {
        // given
        long jobExecutionId = 1L;
        when(auctionRepository.findAllByStatus(AuctionStatus.APPROVED))
                .thenReturn(Collections.emptyList());

        // when
        RepeatStatus status = auctionActivationTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(auctionRepository, times(1)).findAllByStatus(AuctionStatus.APPROVED);
        verifyNoInteractions(mainAuctionLifecycleClient);
    }

    @Test
    void execute_여러_경매중_예외가_발생하면_전체_처리_후_step이_실패한다() throws Exception {
        // given
        long jobExecutionId = 2L;

        Auction auction1 = mockAuction(1L);
        Auction auction2 = mockAuction(2L);
        Auction auction3 = mockAuction(3L);

        when(auctionRepository.findAllByStatus(AuctionStatus.APPROVED))
                .thenReturn(List.of(auction1, auction2, auction3));

        when(mainAuctionLifecycleClient.activate(eq(1L), eq(jobExecutionId))).thenReturn(true);
        when(mainAuctionLifecycleClient.activate(eq(2L), eq(jobExecutionId))).thenReturn(false);
        when(mainAuctionLifecycleClient.activate(eq(3L), eq(jobExecutionId)))
                .thenThrow(new RuntimeException("internal api error"));

        ChunkContext chunkContext = newChunkContext(jobExecutionId);
        StepContribution contribution = mock(StepContribution.class);

        // when & then: 루프는 전부 실행(건별 스킵)하되 failedCount>0이면 step 실패
        assertThatThrownBy(() -> auctionActivationTasklet.execute(contribution, chunkContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("1건 실패");

        verify(mainAuctionLifecycleClient, times(1)).activate(eq(1L), eq(jobExecutionId));
        verify(mainAuctionLifecycleClient, times(1)).activate(eq(2L), eq(jobExecutionId));
        verify(mainAuctionLifecycleClient, times(1)).activate(eq(3L), eq(jobExecutionId));
    }

    @Test
    void execute_모든_경매가_true를_반환하면_전부_활성화된_것으로_집계된다() throws Exception {
        // given
        long jobExecutionId = 3L;

        Auction auction1 = mockAuction(10L);
        Auction auction2 = mockAuction(11L);

        when(auctionRepository.findAllByStatus(AuctionStatus.APPROVED))
                .thenReturn(List.of(auction1, auction2));

        when(mainAuctionLifecycleClient.activate(eq(10L), eq(jobExecutionId))).thenReturn(true);
        when(mainAuctionLifecycleClient.activate(eq(11L), eq(jobExecutionId))).thenReturn(true);

        // when
        RepeatStatus status = auctionActivationTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(mainAuctionLifecycleClient, times(2)).activate(anyLong(), eq(jobExecutionId));
    }
}
