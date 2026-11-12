package ru.dependencyhell;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dependencyhell.model.ApiModels.ResolutionRequest;
import ru.dependencyhell.model.ApiModels.ResolvedPackage;
import ru.dependencyhell.service.ResolutionService;

@RestController
public class InternalResolutionController {
    private final ResolutionService service;
    public InternalResolutionController(ResolutionService service) { this.service = service; }

    @PostMapping("/api/internal/resolve")
    public ResolvedPackage resolve(@RequestBody ResolutionRequest request) { return service.resolve(request); }
}
