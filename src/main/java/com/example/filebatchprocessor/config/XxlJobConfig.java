package com.example.filebatchprocessor.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XxlJobConfig {
    private Logger logger = LoggerFactory.getLogger(XxlJobConfig.class);

    @Value("${xxl.job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:file-batch-executor}")
    private String appName;

    @Value("${xxl.job.executor.port:9999}")
    private int port;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        // 如果 admin 地址为空，跳过初始化（允许在没有 XXL-Job Admin 的情况下运行）
        if (adminAddresses == null || adminAddresses.trim().isEmpty()) {
            logger.warn("XXL-Job Admin addresses is not configured, skipping XXL-Job executor initialization.");
            logger.warn("If you need XXL-Job, please configure 'xxl.job.admin.addresses' in application.yml");
            return null;
        }
        
        logger.info(">>>>>>>>>>>> xxl-job config init. Admin addresses: {}", adminAddresses);
        
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appName);
        xxlJobSpringExecutor.setPort(port);
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            xxlJobSpringExecutor.setAccessToken(accessToken);
        }
        xxlJobSpringExecutor.setLogRetentionDays(30);

        return xxlJobSpringExecutor;
    }
}
