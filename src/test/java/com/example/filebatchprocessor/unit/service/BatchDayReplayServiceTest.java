package com.example.filebatchprocessor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.model.BatchDayReplayScope;
import com.example.filebatchprocessor.model.BatchDayReplaySession;
import com.example.filebatchprocessor.model.BatchDayReplayStatus;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.repository.BatchDayReplayEntryRepository;
import com.example.filebatchprocessor.repository.BatchDayReplaySessionRepository;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.service.BatchDayReplayService;
import com.example.filebatchprocessor.service.BatchDayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchDayReplayServiceTest {

    @Test
    void shouldCreateReplaySessionAndEnqueueFailedTasks() {
        BatchDayReplaySessionRepository sessionRepository = mock(BatchDayReplaySessionRepository.class);
        BatchDayReplayEntryRepository entryRepository = mock(BatchDayReplayEntryRepository.class);
        BusinessJobInstanceRepository jobInstanceRepository = mock(BusinessJobInstanceRepository.class);
        TaskExecutionStateRepository taskExecutionStateRepository = mock(TaskExecutionStateRepository.class);
        TaskSchedulerService taskSchedulerService = mock(TaskSchedulerService.class);
        BatchDayService batchDayService = mock(BatchDayService.class);

        BusinessJobInstance failed = new BusinessJobInstance();
        failed.setId(101L);
        failed.setTaskId("import-main");
        failed.setJobName("importJob");
        failed.setBizDate("2026-03-04");
        failed.setStatus("FAILED");

        when(sessionRepository.findFirstByTenantIdAndCalendarCodeAndBizDateAndStatusIn(
                        eq("default"), eq("default"), eq(LocalDate.parse("2026-03-04")), any()))
                .thenReturn(Optional.empty());
        when(jobInstanceRepository.findByBizDateAndStatusInOrderByCreatedAtDesc(eq("2026-03-04"), any()))
                .thenReturn(List.of(failed));
        when(sessionRepository.save(any(BatchDayReplaySession.class))).thenAnswer(invocation -> {
            BatchDayReplaySession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(7L);
            }
            return session;
        });
        when(taskSchedulerService.enqueueManualRerun(
                        eq("import-main"), eq("2026-03-04"), eq("BATCH_DAY_REPLAY:fix bad input"), eq("ops")))
                .thenReturn(new TaskSchedulerService.ManualEnqueueResult(
                        "import-main", "importJob", "2026-03-04", "manual-1", "rk-1"));

        BatchDayReplayService service = new BatchDayReplayService(
                sessionRepository,
                entryRepository,
                jobInstanceRepository,
                taskExecutionStateRepository,
                taskSchedulerService,
                batchDayService,
                new ObjectMapper());

        BatchDayReplaySession session = service.submit(
                new BatchDayReplayService.SubmitRequest(
                        null,
                        null,
                        LocalDate.parse("2026-03-04"),
                        BatchDayReplayScope.ALL_FAILED,
                        null,
                        "fix bad input"),
                "ops");

        assertThat(session.getId()).isEqualTo(7L);
        assertThat(session.getTotalCount()).isEqualTo(1);
        verify(batchDayService).markReplaying("default", "default", LocalDate.parse("2026-03-04"), 7L);
        ArgumentCaptor<com.example.filebatchprocessor.model.BatchDayReplayEntry> entryCaptor =
                ArgumentCaptor.forClass(com.example.filebatchprocessor.model.BatchDayReplayEntry.class);
        verify(entryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getTaskId()).isEqualTo("import-main");
        assertThat(entryCaptor.getValue().getRerunId()).isEqualTo("manual-1");
    }

    @Test
    void shouldCompleteBatchDayWhenReplayHasNoCandidates() {
        BatchDayReplaySessionRepository sessionRepository = mock(BatchDayReplaySessionRepository.class);
        BatchDayReplayEntryRepository entryRepository = mock(BatchDayReplayEntryRepository.class);
        BusinessJobInstanceRepository jobInstanceRepository = mock(BusinessJobInstanceRepository.class);
        TaskExecutionStateRepository taskExecutionStateRepository = mock(TaskExecutionStateRepository.class);
        TaskSchedulerService taskSchedulerService = mock(TaskSchedulerService.class);
        BatchDayService batchDayService = mock(BatchDayService.class);

        when(sessionRepository.findFirstByTenantIdAndCalendarCodeAndBizDateAndStatusIn(
                        eq("default"), eq("default"), eq(LocalDate.parse("2026-03-04")), any()))
                .thenReturn(Optional.empty());
        when(jobInstanceRepository.findByBizDateAndStatusInOrderByCreatedAtDesc(eq("2026-03-04"), any()))
                .thenReturn(List.of());
        when(sessionRepository.save(any(BatchDayReplaySession.class))).thenAnswer(invocation -> {
            BatchDayReplaySession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(8L);
            }
            return session;
        });
        when(sessionRepository.findById(8L)).thenAnswer(invocation -> {
            BatchDayReplaySession session = new BatchDayReplaySession();
            session.setId(8L);
            session.setTenantId("default");
            session.setCalendarCode("default");
            session.setBizDate(LocalDate.parse("2026-03-04"));
            session.setStatus(BatchDayReplayStatus.RUNNING);
            return Optional.of(session);
        });
        when(entryRepository.findBySessionIdOrderByIdAsc(8L)).thenReturn(List.of());

        BatchDayReplayService service = new BatchDayReplayService(
                sessionRepository,
                entryRepository,
                jobInstanceRepository,
                taskExecutionStateRepository,
                taskSchedulerService,
                batchDayService,
                new ObjectMapper());

        BatchDayReplaySession session = service.submit(
                new BatchDayReplayService.SubmitRequest(
                        null,
                        null,
                        LocalDate.parse("2026-03-04"),
                        BatchDayReplayScope.ALL_FAILED,
                        null,
                        "nothing to replay"),
                "ops");

        assertThat(session.getStatus()).isEqualTo(BatchDayReplayStatus.SUCCEEDED);
        verify(batchDayService).markReplayCompleted("default", "default", LocalDate.parse("2026-03-04"), 8L, true);
    }
}
