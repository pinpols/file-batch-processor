package com.example.filebatchprocessor.unit.batch.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.scheduler.DependencyResolver;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    @Test
    void shouldReturnSkippedWhenDependencyFailedAndActionSkip() {
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        DependencyResolver resolver = new DependencyResolver(repository);

        TaskExecutionState failedState = new TaskExecutionState();
        failedState.setStatus("FAILED");
        when(repository.findByTaskIdAndBatchDateAndRerunId("upstream-a", "2026-03-04", ""))
                .thenReturn(Optional.of(failedState));

        OrchestrationTaskDefinition task = OrchestrationTaskDefinition.builder()
                .id("downstream")
                .dependencies(List.of("upstream-a"))
                .dependencyFailureActionByTask(Map.of("upstream-a", "SKIP"))
                .dependencyTimeoutByTask(Map.of())
                .parameters(Map.of())
                .build();

        assertEquals(DependencyResolver.DependencyState.SKIPPED, resolver.resolve(task, "2026-03-04", 600000L, 1000L));
    }

    @Test
    void shouldReturnFailedWhenDependencyWaitTimeoutExceeded() {
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        DependencyResolver resolver = new DependencyResolver(repository);
        when(repository.findByTaskIdAndBatchDateAndRerunId("upstream-a", "2026-03-04", ""))
                .thenReturn(Optional.empty());

        OrchestrationTaskDefinition task = OrchestrationTaskDefinition.builder()
                .id("downstream")
                .dependencies(List.of("upstream-a"))
                .dependencyFailureActionByTask(Map.of("upstream-a", "FAIL"))
                .dependencyTimeoutByTask(Map.of("upstream-a", 3000L))
                .parameters(Map.of())
                .build();

        assertEquals(DependencyResolver.DependencyState.FAILED, resolver.resolve(task, "2026-03-04", 600000L, 5000L));
    }

    @Test
    void shouldResolveCrossDayDependencyByOffset() {
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        DependencyResolver resolver = new DependencyResolver(repository);

        TaskExecutionState successState = new TaskExecutionState();
        successState.setStatus("SUCCESS");
        when(repository.findByTaskIdAndBatchDateAndRerunId("daily-close", "2026-03-03", ""))
                .thenReturn(Optional.of(successState));

        OrchestrationTaskDefinition task = OrchestrationTaskDefinition.builder()
                .id("monthly-report")
                .dependencies(List.of("daily-close"))
                .dependencyBatchDateOffsetDaysByTask(Map.of("daily-close", -1))
                .dependencyTimeoutByTask(Map.of())
                .parameters(Map.of())
                .build();

        assertEquals(DependencyResolver.DependencyState.READY, resolver.resolve(task, "2026-03-04", 600000L, 1000L));
    }
}
