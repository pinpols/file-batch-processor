package com.example.filebatchprocessor.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Getter
@ConfigurationProperties(prefix = "batch.import.parse-error")
public class ImportParseErrorGateProperties {

    private double maxRate = 0.2;

    private long minLines = 50;

    /** 单作业覆盖规则：rules.<jobName>.max-rate / rules.<jobName>.min-lines。 */
    private Map<String, Rule> rules = new HashMap<>();

    // 显式 getter/setter 用于规避当前 Lombok 注解处理差异。

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

        // 显式 getter/setter 用于规避当前 Lombok 注解处理差异。

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
