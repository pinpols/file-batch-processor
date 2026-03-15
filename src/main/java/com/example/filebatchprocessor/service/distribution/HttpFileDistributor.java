package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

@Slf4j
@Component
public class HttpFileDistributor implements FileDistributor {

    private final FileDistributionService fileDistributionService;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public HttpFileDistributor(FileDistributionService fileDistributionService) {
        this.fileDistributionService = fileDistributionService;
    }

    @Override
    public boolean supports(String targetSystem) {
        return "HTTP".equalsIgnoreCase(targetSystem);
    }

    @Override
    public void distribute(FileDistributionTask task) {
        distribute(task, null);
    }

    @Override
    public void distribute(FileDistributionTask task, Long jobInstanceId) {
        if (task == null) {
            return;
        }
        try {
            fileDistributionService.markAsInProgress(task.getId(), jobInstanceId);

            File localFile = new File(task.getFilePath());
            if (!localFile.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Local file not found: " + task.getFilePath());
            }
            String url = task.getTargetAddress();
            if (url == null || url.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "HTTP target URL is required");
            }

            String method = "POST";
            String normalizedMethod = method.toUpperCase(Locale.ROOT);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/octet-stream")
                    .header("X-File-Name", task.getFileName())
                    .method(normalizedMethod, HttpRequest.BodyPublishers.ofFile(localFile.toPath()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                fileDistributionService.markAsSuccess(task.getId(), jobInstanceId, false, null, java.util.Map.of(
                        "httpStatus", response.statusCode()
                ));
            } else {
                fileDistributionService.markAsFailed(task.getId(), "HTTP transfer failed with status " + response.statusCode(), jobInstanceId);
            }
        } catch (Exception e) {
            log.error("HTTP distribution failed for taskId={}", task.getId(), e);
            fileDistributionService.markAsFailed(task.getId(), "HTTP transfer failed: " + e.getMessage(), jobInstanceId);
        }
    }
}
