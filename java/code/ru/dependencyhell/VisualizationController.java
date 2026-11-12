package ru.dependencyhell;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dependencyhell.model.ApiModels.Visualization;
import ru.dependencyhell.service.VisualizationService;

@RestController
public class VisualizationController {
    private final VisualizationService service;
    public VisualizationController(VisualizationService service) { this.service = service; }

    @PostMapping("/api/runs/{id}/visualization")
    public Visualization build(@PathVariable String id) { return service.build(id); }

    @GetMapping("/api/runs/{id}/visualization")
    public Visualization get(@PathVariable String id) { return service.getSaved(id); }
}
