package com.example.filebatchprocessor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroupMember;
import com.example.filebatchprocessor.service.ManifestReconcileService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestReconcileServiceTest {

    private final ManifestReconcileService service = new ManifestReconcileService();

    private Path writeFile(Path dir, String name, String content) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void passesWhenCountAndChecksumMatch(@TempDir Path dir) throws Exception {
        // header + 2 data rows
        Path f = writeFile(dir, "data.csv", "id,name\n1,a\n2,b\n");

        FileReceptionQueue queueRow = new FileReceptionQueue();
        queueRow.setId(10L);
        queueRow.setFileName("data.csv");
        queueRow.setFilePath(f.toString());
        queueRow.setFileHash("ABC123");

        ReceptionGroupMember member = new ReceptionGroupMember();
        member.setExpectedRecordCount(2L);
        member.setExpectedChecksum("ABC123");

        ManifestReconcileService.ReconcileResult result = service.reconcile(member, queueRow);

        assertThat(result.pass()).isTrue();
        assertThat(result.mismatches()).isEmpty();
        assertThat(member.getActualRecordCount()).isEqualTo(2L);
    }

    @Test
    void failsOnCountMismatch(@TempDir Path dir) throws Exception {
        Path f = writeFile(dir, "data.csv", "id,name\n1,a\n2,b\n");

        FileReceptionQueue queueRow = new FileReceptionQueue();
        queueRow.setFilePath(f.toString());
        queueRow.setFileHash("ABC123");

        ReceptionGroupMember member = new ReceptionGroupMember();
        member.setExpectedRecordCount(5L);

        ManifestReconcileService.ReconcileResult result = service.reconcile(member, queueRow);

        assertThat(result.pass()).isFalse();
        assertThat(result.mismatches()).anyMatch(m -> m.contains("record count"));
        assertThat(member.getActualRecordCount()).isEqualTo(2L);
    }

    @Test
    void failsOnChecksumMismatch(@TempDir Path dir) throws Exception {
        Path f = writeFile(dir, "data.csv", "id,name\n1,a\n2,b\n");

        FileReceptionQueue queueRow = new FileReceptionQueue();
        queueRow.setFilePath(f.toString());
        queueRow.setFileHash("BBB");

        ReceptionGroupMember member = new ReceptionGroupMember();
        member.setExpectedChecksum("AAA");

        ManifestReconcileService.ReconcileResult result = service.reconcile(member, queueRow);

        assertThat(result.pass()).isFalse();
        assertThat(result.mismatches()).anyMatch(m -> m.toLowerCase().contains("checksum"));
    }

    @Test
    void failsWhenFileNotArrived() {
        ReceptionGroupMember member = new ReceptionGroupMember();
        member.setExpectedRecordCount(2L);

        ManifestReconcileService.ReconcileResult result = service.reconcile(member, null);

        assertThat(result.pass()).isFalse();
        assertThat(result.mismatches()).anyMatch(m -> m.contains("file not arrived"));
    }
}
