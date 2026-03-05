package com.example.filebatchprocessor.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@Getter
@ConfigurationProperties(prefix = "batch.import.parse-error")
public class ImportParseErrorGateProperties {

    private double maxRate = 0.2;

    private long minLines = 50;

    /**
     * Per job override rules: rules.<jobName>.max-rate / rules.<jobName>.min-lines
     */
    private Map<String, Rule> rules = new HashMap<>();

    // Explicit getters as workaround for Lombok annotation processing issue
    
    public double getMaxRate() {
        return maxRate;
    }

    public void setMaxRate(double maxRate) {
        this.maxRate = maxRate;
    }

    public long getMinLines() {
        return minLines;
    }

    public void setMinLines(long minLines) {
        this.minLines = minLines;
    }

    public Map<String, Rule> getRules() {
        return rules;
    }

    public void setRules(Map<String, Rule> rules) {
        this.rules = rules;
    }

    @Data
    @Getter
    public static class Rule {
        private Double maxRate;
        private Long minLines;
        
        // Explicit getters as workaround for Lombok annotation processing issue
        
        public Double getMaxRate() {
            return maxRate;
        }

        public void setMaxRate(Double maxRate) {
            this.maxRate = maxRate;
        }

        public Long getMinLines() {
            return minLines;
        }

        public void setMinLines(Long minLines) {
            this.minLines = minLines;
        }
    }
}
