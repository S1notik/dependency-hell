package ru.dependencyhell.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dependencyhell.model.ApiModels.LoadOrder;
import ru.dependencyhell.repository.LoadOrderRepository;

@Service
public class LoadOrderStorageService {
    private final LoadOrderRepository repository;
    public LoadOrderStorageService(LoadOrderRepository repository) { this.repository = repository; }

    @Transactional
    public void save(String runId, LoadOrder order) { repository.save(runId, order); }
}
