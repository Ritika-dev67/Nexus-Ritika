package com.nexus.platform;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class NexusController {
    private final NexusStore store;
    private final NexusEngine engine;

    public NexusController(NexusStore store, NexusEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    public record WorkRequest(String id, String type, String body) {}

    @GetMapping("/state")
    public NexusState state() {
        return store.snapshot();
    }

    @GetMapping("/events")
    public Object events(@RequestParam(defaultValue = "30") int limit) {
        return store.recentEvents(Math.min(limit, 100));
    }

    @PostMapping("/work")
    public WorkItem createWork(@RequestBody(required = false) WorkRequest request) {
        if (request == null) request = new WorkRequest(null, "demo", "{\"message\":\"hello\"}");
        return store.addWork(request.type(), request.body(), request.id());
    }

    @PostMapping("/chaos/kill-worker")
    public ResponseEntity<?> killWorker() {
        engine.killWorker();
        return ResponseEntity.ok(Map.of("message", "Worker-01 killed"));
    }

    @PostMapping("/chaos/restart-worker")
    public ResponseEntity<?> restartWorker() {
        engine.restartWorker();
        return ResponseEntity.ok(Map.of("message", "Worker-01 restart requested"));
    }

    @PostMapping("/chaos/slow-worker")
    public ResponseEntity<?> slowWorker(@RequestParam(defaultValue = "true") boolean enabled) {
        engine.slowWorker(enabled);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PostMapping("/chaos/crash-on-start")
    public ResponseEntity<?> crashOnStart(@RequestParam(defaultValue = "true") boolean enabled) {
        engine.crashOnStart(enabled);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PostMapping("/chaos/duplicate-latest")
    public ResponseEntity<?> duplicateLatest() {
        engine.duplicateLatest();
        return ResponseEntity.ok(Map.of("message", "Duplicate delivery simulated"));
    }

    @PostMapping("/releases/bad")
    public ResponseEntity<?> badRelease() {
        engine.deployBadRelease();
        return ResponseEntity.ok(Map.of("message", "Bad release deployed"));
    }

    @PostMapping("/releases/rollback")
    public ResponseEntity<?> rollback() {
        engine.rollback();
        return ResponseEntity.ok(Map.of("message", "Rollback requested"));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        engine.reset();
        return ResponseEntity.ok(Map.of("message", "State reset"));
    }
}
