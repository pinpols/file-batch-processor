package com.example.filebatchprocessor.observability;

import jakarta.annotation.PostConstruct;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerListener;
import org.quartz.impl.matchers.EverythingMatcher;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.listeners.TriggerListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QuartzMetricsRegistrar {

    private static final Logger log = LoggerFactory.getLogger(QuartzMetricsRegistrar.class);

    private final Scheduler scheduler;
    private final BatchMetrics batchMetrics;

    public QuartzMetricsRegistrar(Scheduler scheduler, BatchMetrics batchMetrics) {
        this.scheduler = scheduler;
        this.batchMetrics = batchMetrics;
    }

    @PostConstruct
    public void register() {
        registerGauges();
        registerMisfireListener();
    }

    private void registerGauges() {
        batchMetrics.gauge("quartz_scheduler_started", () -> safeNumber(scheduler::isStarted));
        batchMetrics.gauge("quartz_scheduler_shutdown", () -> safeNumber(scheduler::isShutdown));
        batchMetrics.gauge(
                "quartz_jobs_total",
                () -> safeNumber(
                        () -> scheduler.getJobKeys(GroupMatcher.anyGroup()).size()));
        batchMetrics.gauge(
                "quartz_triggers_total",
                () -> safeNumber(
                        () -> scheduler.getTriggerKeys(GroupMatcher.anyGroup()).size()));
    }

    private void registerMisfireListener() {
        try {
            TriggerListener listener = new TriggerListenerSupport() {
                @Override
                public String getName() {
                    return "quartzMetricsMisfireListener";
                }

                @Override
                public void triggerMisfired(Trigger trigger) {
                    String group = trigger.getKey().getGroup();
                    batchMetrics.counter("quartz_misfire_total", "group", group);
                }
            };
            scheduler.getListenerManager().addTriggerListener(listener, EverythingMatcher.allTriggers());
        } catch (Exception e) {
            log.warn("Failed to register Quartz misfire listener", e);
        }
    }

    private Number safeNumber(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean() ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private Number safeNumber(IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    @FunctionalInterface
    private interface IntSupplier {
        int getAsInt() throws Exception;
    }
}
