package com.example.filebatchprocessor.config;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 批处理统一时区入口。
 *
 * <p>调度、misfire 判断和日历排除不能依赖宿主机默认时区，否则不同机器或容器镜像下会出现账期边界不一致。
 */
@Component
public class BatchTimezoneProvider {

    private final ZoneId zoneId;

    public BatchTimezoneProvider(@Value("${batch.timezone.default-zone:Asia/Shanghai}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
