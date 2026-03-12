package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class TaskGraphManager {

    private final Map<String, OrchestrationTaskDefinition> taskDefinitions = new HashMap<>();

    public synchronized void register(OrchestrationTaskDefinition definition) {
        taskDefinitions.put(definition.getId(), definition);
        validateDag();
    }

    public synchronized OrchestrationTaskDefinition get(String taskId) {
        return taskDefinitions.get(taskId);
    }

    public synchronized Map<String, Object> snapshot() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();
        for (OrchestrationTaskDefinition def : taskDefinitions.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("taskId", def.getId());
            node.put("jobName", def.getJobName());
            node.put("dependencies", def.getDependencies());
            nodes.add(node);
            for (String dep : def.getDependencies()) {
                Map<String, String> edge = new LinkedHashMap<>();
                edge.put("from", dep);
                edge.put("to", def.getId());
                edges.add(edge);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        return result;
    }

    public synchronized List<OrchestrationTaskDefinition> topologicallySorted() {
        validateDag();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();

        for (OrchestrationTaskDefinition def : taskDefinitions.values()) {
            inDegree.putIfAbsent(def.getId(), 0);
            for (String dep : def.getDependencies()) {
                graph.computeIfAbsent(dep, k -> new ArrayList<>()).add(def.getId());
                inDegree.put(def.getId(), inDegree.getOrDefault(def.getId(), 0) + 1);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((k, v) -> {
            if (v == 0) {
                queue.add(k);
            }
        });

        List<OrchestrationTaskDefinition> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            OrchestrationTaskDefinition def = taskDefinitions.get(id);
            if (def != null) {
                ordered.add(def);
            }
            for (String next : graph.getOrDefault(id, Collections.emptyList())) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) {
                    queue.add(next);
                }
            }
        }
        return ordered;
    }

    private void validateDag() {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (OrchestrationTaskDefinition def : taskDefinitions.values()) {
            if (!visited.contains(def.getId())) {
                dfs(def.getId(), visiting, visited);
            }
        }
    }

    private void dfs(String id, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(id)) {
            throw new IllegalStateException("Cycle detected in task DAG at " + id);
        }
        if (visited.contains(id)) {
            return;
        }
        visiting.add(id);
        OrchestrationTaskDefinition def = taskDefinitions.get(id);
        if (def != null) {
            for (String dep : def.getDependencies()) {
                dfs(dep, visiting, visited);
            }
        }
        visiting.remove(id);
        visited.add(id);
    }
}

