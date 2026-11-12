package ru.dependencyhell.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dependencyhell.model.ApiModels.Visualization;
import ru.dependencyhell.repository.VisualizationRepository;

@Service
public class VisualizationStorageService {
    private final VisualizationRepository repository;
    public VisualizationStorageService(VisualizationRepository repository) { this.repository = repository; }

    @Transactional
    public void save(String runId, Visualization result) { repository.save(runId, result); }
}
