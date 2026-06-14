package com.rocketcrew.pocatbatch.job.aireindex;

import com.rocketcrew.pocatbatch.client.MainAiReindexClient;
import com.rocketcrew.pocatbatch.client.dto.ReindexChunkResponse;
import com.rocketcrew.pocatbatch.domain.card.repository.CardRepository;
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
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiReindexTaskletTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private MainAiReindexClient mainAiReindexClient;

    @InjectMocks
    private AiReindexTasklet aiReindexTasklet;

    private ChunkContext newChunkContext(long jobExecutionId) {
        StepContext stepContext = new StepContext(
                MetaDataInstanceFactory.createStepExecution(
                        MetaDataInstanceFactory.createJobExecution(
                                "aiReindexJob", jobExecutionId, jobExecutionId
                        ),
                        "aiReindexStep",
                        1L
                )
        );
        return new ChunkContext(stepContext);
    }

    @Test
    void execute_cursor를_이용해_100개씩_조회하며_빈_결과를_받으면_종료한다() throws Exception {
        // given
        long jobExecutionId = 1L;
        List<Long> firstChunk = List.of(1L, 2L, 3L);

        when(cardRepository.findActiveCardIdsAfter(eq(0L), any(Pageable.class)))
                .thenReturn(firstChunk);
        when(cardRepository.findActiveCardIdsAfter(eq(3L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(mainAiReindexClient.reindexChunk(eq(firstChunk), eq(jobExecutionId)))
                .thenReturn(new ReindexChunkResponse(3, 0, 3, 0, false));

        // when
        RepeatStatus status = aiReindexTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(cardRepository).findActiveCardIdsAfter(eq(0L), any(Pageable.class));
        verify(cardRepository).findActiveCardIdsAfter(eq(3L), any(Pageable.class));
        verify(mainAiReindexClient, times(1)).reindexChunk(any(), anyLong());
    }

    @Test
    void execute_첫_조회결과가_빈_경우_바로_종료하고_클라이언트를_호출하지_않는다() throws Exception {
        // given
        long jobExecutionId = 2L;

        when(cardRepository.findActiveCardIdsAfter(eq(0L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // when
        RepeatStatus status = aiReindexTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(cardRepository, times(1)).findActiveCardIdsAfter(eq(0L), any(Pageable.class));
        verifyNoInteractions(mainAiReindexClient);
    }

    @Test
    void execute_rateLimited_true_응답을_받으면_즉시_종료하고_추가_조회_호출을_하지_않는다() throws Exception {
        // given
        long jobExecutionId = 3L;
        List<Long> firstChunk = List.of(10L, 11L, 12L);

        when(cardRepository.findActiveCardIdsAfter(eq(0L), any(Pageable.class)))
                .thenReturn(firstChunk);
        when(mainAiReindexClient.reindexChunk(eq(firstChunk), eq(jobExecutionId)))
                .thenReturn(new ReindexChunkResponse(3, 0, 0, 0, true));

        // when
        RepeatStatus status = aiReindexTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(cardRepository, times(1)).findActiveCardIdsAfter(any(), any(Pageable.class));
        verify(mainAiReindexClient, times(1)).reindexChunk(any(), anyLong());
    }

    @Test
    void execute_여러_청크를_순차적으로_처리하며_cursor를_갱신한다() throws Exception {
        // given
        long jobExecutionId = 4L;
        List<Long> firstChunk = List.of(1L, 2L, 3L);
        List<Long> secondChunk = List.of(4L, 5L);

        when(cardRepository.findActiveCardIdsAfter(eq(0L), any(Pageable.class)))
                .thenReturn(firstChunk);
        when(cardRepository.findActiveCardIdsAfter(eq(3L), any(Pageable.class)))
                .thenReturn(secondChunk);
        when(cardRepository.findActiveCardIdsAfter(eq(5L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(mainAiReindexClient.reindexChunk(eq(firstChunk), eq(jobExecutionId)))
                .thenReturn(new ReindexChunkResponse(3, 0, 3, 0, false));
        when(mainAiReindexClient.reindexChunk(eq(secondChunk), eq(jobExecutionId)))
                .thenReturn(new ReindexChunkResponse(2, 0, 2, 0, false));

        // when
        RepeatStatus status = aiReindexTasklet.execute(
                mock(StepContribution.class), newChunkContext(jobExecutionId));

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(cardRepository).findActiveCardIdsAfter(eq(0L), any(Pageable.class));
        verify(cardRepository).findActiveCardIdsAfter(eq(3L), any(Pageable.class));
        verify(cardRepository).findActiveCardIdsAfter(eq(5L), any(Pageable.class));
        verify(mainAiReindexClient, times(2)).reindexChunk(any(), anyLong());
    }
}
