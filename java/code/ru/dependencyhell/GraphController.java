package ru.dependencyhell;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dependencyhell.model.ApiModels.GraphInput;
import ru.dependencyhell.model.ApiModels.GraphResult;
import ru.dependencyhell.service.GraphService;

@RestController
public class GraphController {
    private final GraphService service;
    public GraphController(GraphService service) { this.service = service; }

    @PostMapping("/api/runs/{id}/graph")
    public GraphResult build(@PathVariable String id, @RequestBody(required = false) GraphInput input) { return service.build(id, input); }

    @GetMapping("/api/runs/{id}/graph")
    public GraphResult get(@PathVariable String id) { return service.getSaved(id); }
}
