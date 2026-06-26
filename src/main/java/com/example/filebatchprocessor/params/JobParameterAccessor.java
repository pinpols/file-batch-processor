package com.example.filebatchprocessor.params;

import java.util.Map;
import org.springframework.batch.core.job.parameters.JobParameters;

public class JobParameterAccessor {

    private final JobParameters jobParameters;
    private final Map<String, Object> jobParameterMap;

    public JobParameterAccessor(JobParameters jobParameters) {
        this.jobParameters = jobParameters;
        this.jobParameterMap = null;
    }

    public JobParameterAccessor(Map<String, Object> jobParameterMap) {
        this.jobParameters = null;
        this.jobParameterMap = jobParameterMap;
    }

    public String getString(String key) {
        if (jobParameters != null) {
            try {
                return jobParameters.getString(key);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (jobParameterMap != null) {
            Object value = jobParameterMap.get(key);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    public String getRequiredString(String key) {
        String v = getString(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v;
    }

    public Integer getInt(String key) {
        if (jobParameters != null) {
            try {
                Long v = jobParameters.getLong(key);
                return v == null ? null : v.intValue();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (jobParameterMap != null) {
            Object value = jobParameterMap.get(key);
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public int getIntOrDefault(String key, int defaultValue) {
        Integer v = getInt(key);
        return v == null ? defaultValue : v;
    }

    public String getStringOrDefault(String key, String defaultValue) {
        String v = getString(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
