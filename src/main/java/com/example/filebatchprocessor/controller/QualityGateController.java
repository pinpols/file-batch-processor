package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.QualityGateResult;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
public class QualityGateController {

    private final QualityGateResultRepository repository;

    public QualityGateController(QualityGateResultRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/gates")
    public List<QualityGateResult> latest() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    @GetMapping("/gates/job/{jobName}")
    public List<QualityGateResult> byJob(@PathVariable String jobName) {
        return repository.findTop200ByJobNameOrderByCreatedAtDesc(jobName);
    }
}
