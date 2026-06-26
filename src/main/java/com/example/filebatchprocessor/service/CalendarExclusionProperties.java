package com.example.filebatchprocessor.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 日历排除配置属性
 */
@Data
@ConfigurationProperties(prefix = "orchestration.scheduler.calendar-exclusion")
@Service
public class CalendarExclusionProperties {

    /**
     * 是否排除周末
     */
    private boolean excludeWeekends = true;

    /**
     * 节假日配置，按日历名称分组
     * 格式：calendarName -> ["YYYY-MM-DD", "MM-DD"]
     */
    private Map<String, List<String>> holidays = new HashMap<>();

    /**
     * 排除的时间窗口，按日历名称分组
     * 格式：calendarName -> ["HH:mm-HH:mm"]
     */
    private Map<String, List<String>> excludeTimeWindows = new HashMap<>();

    /**
     * 默认日历名称
     */
    private String defaultCalendar = "default";

    /**
     * 是否启用日历排除功能
     */
    private boolean enabled = true;
}
