package dev.coala.api;

import dev.coala.demo.DemoUseCaseDescriptor;
import dev.coala.demo.DemoUseCaseService;
import dev.coala.demo.UseCaseRunResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CoalaController {
    private final DemoUseCaseService useCases;

    public CoalaController(DemoUseCaseService useCases) { this.useCases = useCases; }

    @GetMapping("/use-cases")
    public List<DemoUseCaseDescriptor> listUseCases() { return useCases.list(); }

    @PostMapping("/use-cases/{id}/run")
    public UseCaseRunResponse run(@PathVariable String id, @RequestBody RunRequest request) {
        return useCases.run(id, request.goal(), request.maxCycles(), request.sessionId());
    }
}
