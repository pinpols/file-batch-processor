package com.example.filebatchprocessor.service;

import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class BatchJobResolver {

    private final ObjectProvider<Map<String, Job>> jobsProvider;
    private final BeanFactory beanFactory;

    public BatchJobResolver(ObjectProvider<Map<String, Job>> jobsProvider, BeanFactory beanFactory) {
        this.jobsProvider = jobsProvider;
        this.beanFactory = beanFactory;
    }

    public Optional<ResolvedJob> resolve(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return Optional.empty();
        }

        Map<String, Job> jobs = jobsProvider.getIfAvailable();
        if (jobs == null || jobs.isEmpty()) {
            return Optional.empty();
        }

        Job directMatch = jobs.get(requestedName);
        if (directMatch != null) {
            return Optional.of(new ResolvedJob(requestedName, directMatch.getName(), directMatch));
        }

        if (beanFactory.containsBean(requestedName) && beanFactory.isTypeMatch(requestedName, Job.class)) {
            Job beanMatch = beanFactory.getBean(requestedName, Job.class);
            return Optional.of(new ResolvedJob(requestedName, beanMatch.getName(), beanMatch));
        }

        for (Map.Entry<String, Job> entry : jobs.entrySet()) {
            Job job = entry.getValue();
            if (requestedName.equals(job.getName())) {
                return Optional.of(new ResolvedJob(entry.getKey(), job.getName(), job));
            }
        }

        return Optional.empty();
    }

    public String describeAvailableJobs() {
        TreeSet<String> names = new TreeSet<>();
        Map<String, Job> jobs = jobsProvider.getIfAvailable();
        if (jobs != null) {
            for (Map.Entry<String, Job> entry : jobs.entrySet()) {
                names.add(entry.getKey());
                names.add(entry.getValue().getName());
            }
        }
        return names.toString();
    }

    public record ResolvedJob(String beanName, String logicalName, Job job) {}
}
