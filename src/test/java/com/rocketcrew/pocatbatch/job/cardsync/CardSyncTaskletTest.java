package com.rocketcrew.pocatbatch.job.cardsync;

import com.rocketcrew.pocatbatch.client.MainCardSyncClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardSyncTaskletTest {

    @Mock
    private MainCardSyncClient mainCardSyncClient;

    @InjectMocks
    private CardSyncTasklet cardSyncTasklet;

    private ChunkContext newChunkContext(long jobExecutionId) {
        StepContext stepContext = new StepContext(
                MetaDataInstanceFactory.createStepExecution(
                        MetaDataInstanceFactory.createJobExecution(
                                "cardSyncJob", jobExecutionId, jobExecutionId
                        ),
                        "cardSyncStep",
                        1L
                )
        );
        return new ChunkContext(stepContext);
    }

    @Test
    void execute_정상_호출시_mainCardSyncClient_triggerSync를_1회_호출한다() throws Exception {
        // given
        long jobExecutionId = 1L;

        // when
        RepeatStatus status = cardSyncTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(mainCardSyncClient, times(1)).triggerSync(jobExecutionId);
    }

    @Test
    void execute_클라이언트가_예외를_던지면_step이_실패한다() {
        // given
        long jobExecutionId = 2L;
        doThrow(new RuntimeException("internal api error"))
                .when(mainCardSyncClient).triggerSync(jobExecutionId);

        // when & then
        assertThatThrownBy(() -> cardSyncTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId)))
                .isInstanceOf(RuntimeException.class);

        verify(mainCardSyncClient, times(1)).triggerSync(jobExecutionId);
    }
}
