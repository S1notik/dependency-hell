package ru.dependencyhell;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dependencyhell.model.ApiModels.LoadOrder;
import ru.dependencyhell.service.LoadOrderService;

@RestController
public class LoadOrderController {
    private final LoadOrderService service;
    public LoadOrderController(LoadOrderService service) { this.service = service; }

    @PostMapping("/api/runs/{id}/load-order")
    public LoadOrder build(@PathVariable String id) { return service.build(id); }

    @GetMapping("/api/runs/{id}/load-order")
    public LoadOrder get(@PathVariable String id) { return service.getSaved(id); }
}
