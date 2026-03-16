package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.service.CalendarExclusionService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CalendarExclusionServiceIT extends PostgresContainerSupport {

    @Autowired
    private CalendarExclusionService calendarExclusionService;

    @Test
    void shouldExcludeWeekends() {
        java.time.Instant friday = java.time.Instant.parse("2026-03-06T10:00:00Z");
        java.time.Instant saturday = java.time.Instant.parse("2026-03-07T10:00:00Z");

        assertFalse(calendarExclusionService.isExcluded(friday, "default"), "Friday should not be excluded");
        assertTrue(calendarExclusionService.isExcluded(saturday, "default"), "Saturday should be excluded");
    }

    @Test
    void shouldExcludeHolidays() {
        java.time.Instant newYear = java.time.Instant.parse("2026-01-01T10:00:00Z");

        assertTrue(calendarExclusionService.isExcluded(newYear, "default"), "New Year should be excluded");
    }

    @Test
    void shouldExcludeNightTime() {
        java.time.Instant nightTime = java.time.Instant.parse("2026-03-06T23:00:00Z");

        assertTrue(calendarExclusionService.isExcluded(nightTime, "default"), "Night time should be excluded");
    }

    @Test
    void shouldGetNextAvailableTime() {
        java.time.Instant saturdayNight = java.time.Instant.parse("2026-03-07T23:00:00Z");

        java.time.Instant nextAvailable = calendarExclusionService.getNextAvailableTime(saturdayNight, "default");

        assertNotNull(nextAvailable, "Should return next available time");
        assertTrue(nextAvailable.isAfter(saturdayNight), "Next available should be after input time");
    }

    @Test
    void shouldReturnCalendarInfo() {
        assertNotNull(calendarExclusionService.getCalendarInfo("default"), "Should return calendar info");
    }
}
