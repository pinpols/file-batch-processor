package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.manifest.ParsedManifest;
import com.example.filebatchprocessor.manifest.ParsedManifest.ExpectedFile;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupMemberRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import com.example.filebatchprocessor.service.ReceptionGroupService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReceptionGroupServiceTest {

    private ReceptionGroupRepository groupRepo;
    private ReceptionGroupMemberRepository memberRepo;
    private FileReceptionQueueRepository queueRepo;
    private ReceptionGroupService service;

    @BeforeEach
    void setUp() {
        groupRepo = mock(ReceptionGroupRepository.class);
        memberRepo = mock(ReceptionGroupMemberRepository.class);
        queueRepo = mock(FileReceptionQueueRepository.class);
        service = new ReceptionGroupService(groupRepo, memberRepo, queueRepo);
        ReflectionTestUtils.setField(service, "ttlMinutes", 360L);
    }

    private ParsedManifest twoFileManifest() {
        return new ParsedManifest(
                "MF-001",
                "ERP",
                "2026-06-27",
                List.of(
                        new ExpectedFile("a.csv", 10L, "abc", "MD5", true),
                        new ExpectedFile("b.csv", 20L, "def", "MD5", true)));
    }

    @Test
    void registerCreatesGroupAndMembers() {
        when(groupRepo.findByManifestId("MF-001")).thenReturn(Optional.empty());
        when(groupRepo.save(any(ReceptionGroup.class))).thenAnswer(invocation -> {
            ReceptionGroup g = invocation.getArgument(0);
            if (g.getId() == null) {
                g.setId(1L);
            }
            return g;
        });
        when(queueRepo.findByFileName(anyString())).thenReturn(Optional.empty());

        ReceptionGroup result = service.registerFromManifest(twoFileManifest());

        verify(groupRepo, times(1)).save(any(ReceptionGroup.class));
        verify(memberRepo, times(2)).save(any());
        assertEquals(2, result.getTotalMembers());
        assertEquals("WAITING_FILES", result.getStatus());
    }

    @Test
    void registerIsIdempotent() {
        ReceptionGroup existing = new ReceptionGroup();
        existing.setId(99L);
        existing.setManifestId("MF-001");
        when(groupRepo.findByManifestId("MF-001")).thenReturn(Optional.of(existing));

        ReceptionGroup result = service.registerFromManifest(twoFileManifest());

        assertSame(existing, result);
        verify(groupRepo, never()).save(any());
        verify(memberRepo, never()).save(any());
    }
}
