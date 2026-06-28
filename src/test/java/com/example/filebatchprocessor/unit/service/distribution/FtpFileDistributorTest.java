package com.example.filebatchprocessor.service.distribution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import org.junit.jupiter.api.Test;

/**
 * SPI 等价性护栏:FtpFileDistributor 与 SFTP/HTTP 一致,自包含传输并只回调状态机方法。
 *
 * <p>装配 + 校验失败 + 状态回调契约在此覆盖。真实 FTP 传输成功/失败路径需要 FTP server,
 * 由集成/手工验证覆盖,此处不单测。
 */
class FtpFileDistributorTest {

    @Test
    void shouldDoNothingWhenTaskIsNull() {
        FileDistributionService service = mock(FileDistributionService.class);
        DistributionTargetValidator validator = mock(DistributionTargetValidator.class);
        FtpFileDistributor distributor = new FtpFileDistributor(service, validator);

        distributor.distribute(null);

        verifyNoInteractions(service);
        verifyNoInteractions(validator);
    }

    @Test
    void shouldFailWhenTargetAddressIsBlank() {
        FileDistributionService service = mock(FileDistributionService.class);
        DistributionTargetValidator validator = mock(DistributionTargetValidator.class);
        FtpFileDistributor distributor = new FtpFileDistributor(service, validator);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(1L);
        task.setTargetSystem("FTP");
        task.setTargetAddress("");

        distributor.distribute(task);

        verify(service).markAsFailed(eq(1L), contains("target address"), any());
        verify(service, never()).markAsSuccess(any(), any(), anyBoolean(), any(), any());
        verify(service, never()).markAsInProgress(any(), any());
    }

    @Test
    void shouldFailWithInvalidTargetWhenSsrfValidationBlocks() {
        FileDistributionService service = mock(FileDistributionService.class);
        DistributionTargetValidator validator = mock(DistributionTargetValidator.class);
        doThrow(new BusinessException(ErrorCode.INVALID_ARGUMENT, "blocked"))
                .when(validator)
                .validate(any());
        FtpFileDistributor distributor = new FtpFileDistributor(service, validator);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(2L);
        task.setTargetSystem("FTP");
        task.setTargetAddress("ftp://169.254.169.254/upload");
        task.setFilePath("/tmp/whatever.txt");

        distributor.distribute(task);

        verify(service).markAsFailed(eq(2L), contains("Invalid FTP target"), any());
        verify(service, never()).markAsSuccess(any(), any(), anyBoolean(), any(), any());
        verify(service, never()).markAsInProgress(any(), any());
    }
}
