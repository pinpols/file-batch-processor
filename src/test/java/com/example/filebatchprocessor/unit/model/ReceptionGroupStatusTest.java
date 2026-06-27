package com.example.filebatchprocessor.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.model.ReceptionGroupStatus;
import org.junit.jupiter.api.Test;

class ReceptionGroupStatusTest {
    @Test
    void hasExpectedStates() {
        assertEquals(5, ReceptionGroupStatus.values().length);
        ReceptionGroupStatus.valueOf("WAITING_FILES");
        ReceptionGroupStatus.valueOf("COMPLETE");
        ReceptionGroupStatus.valueOf("DISPATCHED");
        ReceptionGroupStatus.valueOf("EXPIRED");
        ReceptionGroupStatus.valueOf("FAILED");
    }
}
