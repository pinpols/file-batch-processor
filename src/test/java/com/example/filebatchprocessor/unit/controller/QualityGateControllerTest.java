package com.example.filebatchprocessor.unit.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.controller.QualityGateController;
import com.example.filebatchprocessor.model.QualityGateResult;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityGateControllerTest {

    private final QualityGateResultRepository repository = mock(QualityGateResultRepository.class);

    @Test
    void shouldReturnLatest() {
        QualityGateResult r = new QualityGateResult();
        r.setId(1L);
        when(repository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(r));

        QualityGateController controller = new QualityGateController(repository);
        List<QualityGateResult> out = controller.latest();
        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).getId());
    }

    @Test
    void shouldReturnByJob() {
        QualityGateResult r = new QualityGateResult();
        r.setId(2L);
        r.setJobName("importJob");
        when(repository.findTop200ByJobNameOrderByCreatedAtDesc("importJob")).thenReturn(List.of(r));

        QualityGateController controller = new QualityGateController(repository);
        List<QualityGateResult> out = controller.byJob("importJob");
        assertEquals(1, out.size());
        assertEquals("importJob", out.get(0).getJobName());
    }
}
