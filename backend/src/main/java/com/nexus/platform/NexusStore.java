package com.nexus.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class NexusStore {
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path stateFile = Path.of("nexus-state.json");
    private NexusState state;

    public NexusStore() {
        state = load();
    }

    private NexusState load() {
        try {
            if (Files.exists(stateFile)) {
                NexusState loaded = mapper.readValue(stateFile.toFile(), NexusState.class);
                if (loaded.services == null || loaded.services.isEmpty()) {
                    loaded.services = new NexusState().services;
                }
                if (loaded.events == null) loaded.events = new ArrayList<>();
                if (loaded.work == null) loaded.work = new java.util.LinkedHashMap<>();
                if (loaded.releases == null) loaded.releases = new ArrayList<>();
                return loaded;
            }
        } catch (Exception ignored) {
            // Start clean if a corrupted local state file is found.
        }
        NexusState fresh = new NexusState();
        save(fresh);
        return fresh;
    }

    public synchronized NexusState snapshot() {
        return state;
    }

    public synchronized void save() {
        save(state);
    }

    private void save(NexusState value) {
        try {
            mapper.writeValue(stateFile.toFile(), value);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist NEXUS state", e);
        }
    }

    public synchronized WorkItem addWork(String type, String body, String requestedId) {
        String id = requestedId == null || requestedId.isBlank()
                ? "JOB-" + state.nextJobNumber++
                : requestedId;

        if (state.work.containsKey(id)) {
            WorkItem existing = state.work.get(id);
            existing.duplicate = true;
            existing.updatedAt = java.time.Instant.now();
            addEvent("DUPLICATE", "Duplicate work delivery detected: " + id,
                    "The work ID already exists; visible processing is not repeated.");
            save();
            return existing;
        }

        WorkItem item = new WorkItem(id, type == null ? "demo" : type, body == null ? "{}" : body);
        state.work.put(id, item);
        addEvent("WORK_ACCEPTED", "Work accepted: " + id,
                "NEXUS persisted the accepted work before processing.");
        save();
        return item;
    }

    public synchronized Optional<WorkItem> findWork(String id) {
        return Optional.ofNullable(state.work.get(id));
    }

    public synchronized void addEvent(String type, String message, String reason) {
        state.events.add(new Event(type, message, reason));
        if (state.events.size() > 500) {
            state.events.remove(0);
        }
    }

    public synchronized List<Event> recentEvents(int limit) {
        return state.events.stream()
                .sorted(Comparator.comparing((Event e) -> e.timestamp).reversed())
                .limit(limit)
                .toList();
    }
}
