package com.example.filebatchprocessor.batch.scheduler;

public enum TaskPriority {
    LOW(1),
    NORMAL(5),
    HIGH(10),
    CRITICAL(20);

    private final int weight;

    TaskPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
