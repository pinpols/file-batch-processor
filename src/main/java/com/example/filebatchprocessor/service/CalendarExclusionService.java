package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.config.BatchTimezoneProvider;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 日历排除服务
 * 支持节假日和调度窗口的排除功能
 */
@Slf4j
@Service
public class CalendarExclusionService {

    private final CalendarExclusionProperties properties;
    private final ZoneId zoneId;
    private final Map<String, Set<LocalDate>> holidayCache = new ConcurrentHashMap<>();
    private final Map<String, List<TimeWindow>> timeWindowCache = new ConcurrentHashMap<>();

    public CalendarExclusionService(CalendarExclusionProperties properties, BatchTimezoneProvider timezoneProvider) {
        this.properties = properties;
        this.zoneId = timezoneProvider.zoneId();
        initializeHolidays();
        initializeTimeWindows();
    }

    /**
     * 检查指定时间是否被排除
     */
    public boolean isExcluded(Instant instant, String calendarName) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, zoneId);
        LocalDate date = localDateTime.toLocalDate();
        LocalTime time = localDateTime.toLocalTime();

        // 检查节假日排除
        if (isHoliday(date, calendarName)) {
            log.debug("Date {} is excluded due to holiday for calendar: {}", date, calendarName);
            return true;
        }

        // 检查时间窗口排除
        if (isTimeWindowExcluded(time, calendarName)) {
            log.debug("Time {} is excluded due to time window for calendar: {}", time, calendarName);
            return true;
        }

        return false;
    }

    /**
     * 获取下一个可执行的时间
     */
    public Instant getNextAvailableTime(Instant fromTime, String calendarName) {
        Instant currentTime = fromTime;
        int maxIterations = 7 * 24 * 60; // 最多查找7天
        int iterations = 0;

        while (iterations < maxIterations) {
            if (!isExcluded(currentTime, calendarName)) {
                return currentTime;
            }

            // 移动到下一分钟
            currentTime = currentTime.plus(1, ChronoUnit.MINUTES);
            iterations++;
        }

        log.warn("Could not find available time within {} iterations for calendar: {}", maxIterations, calendarName);
        return fromTime.plus(7, ChronoUnit.DAYS); // 默认返回7天后
    }

    /**
     * 检查是否为节假日
     */
    private boolean isHoliday(LocalDate date, String calendarName) {
        Set<LocalDate> holidays = holidayCache.get(calendarName);
        if (holidays == null) {
            return false;
        }

        // 检查固定节假日
        if (holidays.contains(date)) {
            return true;
        }

        // 检查周末（如果配置了排除周末）
        if (properties.isExcludeWeekends()) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否在排除的时间窗口内
     */
    private boolean isTimeWindowExcluded(LocalTime time, String calendarName) {
        List<TimeWindow> windows = timeWindowCache.get(calendarName);
        if (windows == null || windows.isEmpty()) {
            return false;
        }

        return windows.stream().anyMatch(window -> window.contains(time));
    }

    /**
     * 初始化节假日数据
     */
    private void initializeHolidays() {
        for (Map.Entry<String, List<String>> entry : properties.getHolidays().entrySet()) {
            String calendarName = entry.getKey();
            List<String> holidayStrings = entry.getValue();

            Set<LocalDate> holidays = holidayStrings.stream()
                    .map(this::parseHolidayString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            holidayCache.put(calendarName, holidays);
            log.info("Initialized {} holidays for calendar: {}", holidays.size(), calendarName);
        }
    }

    /**
     * 初始化时间窗口数据
     */
    private void initializeTimeWindows() {
        for (Map.Entry<String, List<String>> entry :
                properties.getExcludeTimeWindows().entrySet()) {
            String calendarName = entry.getKey();
            List<String> windowStrings = entry.getValue();

            List<TimeWindow> windows = windowStrings.stream()
                    .map(this::parseTimeWindowString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            timeWindowCache.put(calendarName, windows);
            log.info("Initialized {} time windows for calendar: {}", windows.size(), calendarName);
        }
    }

    /**
     * 解析节假日字符串
     * 支持格式：YYYY-MM-DD 或 MM-DD（每年重复）
     */
    private LocalDate parseHolidayString(String holidayString) {
        try {
            String[] parts = holidayString.split("-");
            if (parts.length == 3) {
                // 完整日期：YYYY-MM-DD
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } else if (parts.length == 2) {
                // 重复日期：MM-DD，使用当前年份
                int year = Year.now().getValue();
                return LocalDate.of(year, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
        } catch (Exception e) {
            log.warn("Failed to parse holiday string: {}", holidayString, e);
        }
        return null;
    }

    /**
     * 解析时间窗口字符串
     * 支持格式：HH:mm-HH:mm
     */
    private TimeWindow parseTimeWindowString(String windowString) {
        try {
            String[] parts = windowString.split("-");
            if (parts.length == 2) {
                LocalTime startTime = LocalTime.parse(parts[0]);
                LocalTime endTime = LocalTime.parse(parts[1]);
                return new TimeWindow(startTime, endTime);
            }
        } catch (Exception e) {
            log.warn("Failed to parse time window string: {}", windowString, e);
        }
        return null;
    }

    /**
     * 添加动态节假日
     */
    public void addHoliday(String calendarName, LocalDate date) {
        holidayCache.computeIfAbsent(calendarName, k -> new HashSet<>()).add(date);
        log.info("Added holiday {} to calendar: {}", date, calendarName);
    }

    /**
     * 移除节假日
     */
    public void removeHoliday(String calendarName, LocalDate date) {
        Set<LocalDate> holidays = holidayCache.get(calendarName);
        if (holidays != null) {
            holidays.remove(date);
            log.info("Removed holiday {} from calendar: {}", date, calendarName);
        }
    }

    /**
     * 添加时间窗口
     */
    public void addTimeWindow(String calendarName, LocalTime startTime, LocalTime endTime) {
        timeWindowCache.computeIfAbsent(calendarName, k -> new ArrayList<>()).add(new TimeWindow(startTime, endTime));
        log.info("Added time window {}-{} to calendar: {}", startTime, endTime, calendarName);
    }

    /**
     * 获取日历信息
     */
    public CalendarInfo getCalendarInfo(String calendarName) {
        Set<LocalDate> holidays = holidayCache.getOrDefault(calendarName, Collections.emptySet());
        List<TimeWindow> timeWindows = timeWindowCache.getOrDefault(calendarName, Collections.emptyList());

        return CalendarInfo.builder()
                .calendarName(calendarName)
                .holidays(new ArrayList<>(holidays))
                .timeWindows(timeWindows)
                .excludeWeekends(properties.isExcludeWeekends())
                .build();
    }

    /**
     * 时间窗口类
     */
    @Data
    public static class TimeWindow {
        private final LocalTime startTime;
        private final LocalTime endTime;

        public TimeWindow(LocalTime startTime, LocalTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public boolean contains(LocalTime time) {
            if (startTime.isBefore(endTime)) {
                // 正常情况：09:00-17:00
                return !time.isBefore(startTime) && !time.isAfter(endTime);
            } else {
                // 跨天情况：22:00-06:00
                return !time.isBefore(startTime) || !time.isAfter(endTime);
            }
        }
    }

    /**
     * 日历信息
     */
    @Data
    @lombok.Builder
    public static class CalendarInfo {
        private String calendarName;
        private List<LocalDate> holidays;
        private List<TimeWindow> timeWindows;
        private boolean excludeWeekends;
    }
}
