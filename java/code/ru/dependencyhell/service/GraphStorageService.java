package ru.dependencyhell.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dependencyhell.model.ApiModels.GraphResult;
import ru.dependencyhell.repository.GraphRepository;

@Service
public class GraphStorageService {
    private final GraphRepository repository;
    public GraphStorageService(GraphRepository repository) { this.repository = repository; }

    @Transactional
    public void save(String runId, GraphResult graph) { repository.save(runId, graph); }
}
