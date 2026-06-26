package com.example.filebatchprocessor.service.distribution;

import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SftpFileDistributorTest {

    @Test
    void shouldFailFastWhenConfigMissing() {
        FileDistributionService service = mock(FileDistributionService.class);
        SftpConcurrencyLimiter limiter = new SftpConcurrencyLimiter(1);
        SftpFileDistributor distributor = new SftpFileDistributor(service, limiter);

        ReflectionTestUtils.setField(distributor, "sftpHost", "");
        ReflectionTestUtils.setField(distributor, "sftpUsername", "u");
        ReflectionTestUtils.setField(distributor, "sftpPassword", "p");

        FileDistributionTask task = new FileDistributionTask();
        task.setId(1L);
        task.setTargetSystem("SFTP");

        distributor.distribute(task);

        verify(service, times(1)).markAsFailed(1L, "SFTP config missing: sftp.host/username/password", null);
        verify(service, never()).markAsInProgress(any());
    }
}
