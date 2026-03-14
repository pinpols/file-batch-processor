package com.example.filebatchprocessor.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CalendarExclusionServiceIntegrationTest {

    @Autowired
    private CalendarExclusionService calendarExclusionService;

    @Test
    void shouldExcludeWeekends() {
        // Given
        java.time.Instant friday = java.time.Instant.parse("2026-03-06T10:00:00Z");
        java.time.Instant saturday = java.time.Instant.parse("2026-03-07T10:00:00Z");
        java.time.Instant monday = java.time.Instant.parse("2026-03-09T10:00:00Z");

        // When & Then
        assertFalse(calendarExclusionService.isExcluded(friday, "default"), "Friday should not be excluded");
        assertTrue(calendarExclusionService.isExcluded(saturday, "default"), "Saturday should be excluded");
        assertFalse(calendarExclusionService.isExcluded(monday, "default"), "Monday should not be excluded");
    }

    @Test
    void shouldExcludeHolidays() {
        // Given
        java.time.Instant newYear = java.time.Instant.parse("2026-01-01T10:00:00Z");
        java.time.Instant springFestival = java.time.Instant.parse("2026-05-01T10:00:00Z");

        // When & Then
        assertTrue(calendarExclusionService.isExcluded(newYear, "default"), "New Year should be excluded");
        assertTrue(calendarExclusionService.isExcluded(springFestival, "default"), "Spring Festival should be excluded");
    }

    @Test
    void shouldExcludeTimeWindows() {
        // Given
        java.time.Instant nightTime = java.time.Instant.parse("2026-03-06T23:00:00Z");
        java.time.Instant lunchTime = java.time.Instant.parse("2026-03-06T12:30:00Z");
        java.time.Instant workTime = java.time.Instant.parse("2026-03-06T14:00:00Z");

        // When & Then
        assertTrue(calendarExclusionService.isExcluded(nightTime, "default"), "Night time should be excluded");
        assertTrue(calendarExclusionService.isExcluded(lunchTime, "default"), "Lunch time should be excluded");
        assertFalse(calendarExclusionService.isExcluded(workTime, "default"), "Work time should not be excluded");
    }

    @Test
    void shouldGetNextAvailableTime() {
        // Given
        java.time.Instant saturdayNight = java.time.Instant.parse("2026-03-07T23:00:00Z");

        // When
        java.time.Instant nextAvailable = calendarExclusionService.getNextAvailableTime(saturdayNight, "default");

        // Then - should skip to Monday morning in local timezone
        java.time.Instant expected = java.time.ZonedDateTime.of(
                2026, 3, 9, 9, 0, 0, 0, java.time.ZoneId.systemDefault()
        ).toInstant();
        assertEquals(expected, nextAvailable, "Should find next available time on Monday morning");
    }

    @Test
    void shouldHandleMultipleCalendars() {
        // Given
        java.time.Instant workTime = java.time.Instant.parse("2026-03-06T20:00:00Z");

        // When & Then
        assertFalse(calendarExclusionService.isExcluded(workTime, "default"), "Work time should not be excluded in default calendar");
        assertTrue(calendarExclusionService.isExcluded(workTime, "business"), "Work time should be excluded in business calendar");
    }
}
