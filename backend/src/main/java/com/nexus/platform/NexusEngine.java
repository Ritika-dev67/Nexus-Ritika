package com.nexus.platform;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class NexusEngine {
    private static final int MAX_WORK_ATTEMPTS = 3;
    private final NexusStore store;

    public NexusEngine(NexusStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelay = 800)
    public synchronized void processWork() {
        NexusState state = store.snapshot();
        ServiceStatus worker = state.services.get("Worker-01");

        if (worker == null || !"RUNNING".equals(worker.status)) {
            recoverStaleProcessing(state);
            return;
        }

        WorkItem item = state.work.values().stream()
                .filter(w -> "WAITING".equals(w.status))
                .min(Comparator.comparing(w -> w.createdAt))
                .orElse(null);

        if (item == null) {
            return;
        }

        if (worker.crashOnStart) {
            worker.status = "RESTARTING";
            worker.restartAttempts++;
            store.addEvent("WORKER_CRASH", "Worker-01 crashed on startup",
                    "Crash-on-start failure mode is enabled.");
            if (worker.restartAttempts >= worker.maxRestartAttempts) {
                worker.status = "OUT_OF_SERVICE";
                store.addEvent("WORKER_GIVE_UP", "Worker-01 taken out of service",
                        "Maximum worker restart attempts reached.");
            }
            store.save();
            return;
        }

        item.status = "PROCESSING";
        item.worker = worker.name;
        item.attempts++;
        item.updatedAt = Instant.now();
        store.addEvent("WORK_STARTED", item.id + " assigned to Worker-01",
                "Worker accepted the next waiting item.");
        store.save();

        // Simulate slow work without blocking the scheduler for too long.
        if (worker.slow) {
            try {
                Thread.sleep(1800);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // If the worker was killed during processing, leave the item PROCESSING.
        if (!"RUNNING".equals(worker.status)) {
            return;
        }

        item.status = "COMPLETED";
        item.updatedAt = Instant.now();
        store.addEvent("WORK_COMPLETED", item.id + " completed",
                "Worker finished processing successfully.");
        store.save();
    }

    private void recoverStaleProcessing(NexusState state) {
        Instant now = Instant.now();

        for (WorkItem item : state.work.values()) {
            if (!"PROCESSING".equals(item.status)) continue;

            long age = Duration.between(item.updatedAt, now).toMillis();
            if (age < 1000) continue;

            if (item.attempts >= MAX_WORK_ATTEMPTS) {
                item.status = "GIVEN_UP";
                item.lastError = "Retry limit reached";
                item.updatedAt = now;
                store.addEvent("WORK_GIVEN_UP", item.id + " given up",
                        "Processing attempts reached the configured limit.");
            } else {
                item.status = "WAITING";
                item.lastError = "Worker unavailable; retrying";
                item.updatedAt = now;
                store.addEvent("WORK_RETRY", item.id + " returned to waiting",
                        "Worker was unavailable before completion.");
            }
        }
        store.save();
    }

    public synchronized void killWorker() {
        ServiceStatus worker = store.snapshot().services.get("Worker-01");
        worker.status = "DOWN";
        store.addEvent("WORKER_KILLED", "Worker-01 was deliberately killed",
                "Operator triggered the kill-worker failure.");
        store.save();
    }

    public synchronized void restartWorker() {
        ServiceStatus worker = store.snapshot().services.get("Worker-01");

        if (worker.restartAttempts >= worker.maxRestartAttempts) {
            worker.status = "OUT_OF_SERVICE";
            store.addEvent("RESTART_BLOCKED", "Worker-01 restart blocked",
                    "Restart limit has been reached.");
            store.save();
            return;
        }

        worker.restartAttempts++;
        if (worker.crashOnStart) {
            worker.status = "RESTARTING";
            store.addEvent("RESTART_FAILED", "Worker-01 failed during restart",
                    "Crash-on-start mode is enabled.");
        } else {
            worker.status = "RUNNING";
            store.addEvent("WORKER_RESTARTED", "Worker-01 restarted",
                    "Operator recovery action completed.");
        }
        store.save();
    }

    public synchronized void slowWorker(boolean enabled) {
        ServiceStatus worker = store.snapshot().services.get("Worker-01");
        worker.slow = enabled;
        store.addEvent("SLOW_MODE", "Worker-01 slow mode " + (enabled ? "enabled" : "disabled"),
                "Operator deliberately changed worker processing speed.");
        store.save();
    }

    public synchronized void crashOnStart(boolean enabled) {
        ServiceStatus worker = store.snapshot().services.get("Worker-01");
        worker.crashOnStart = enabled;
        store.addEvent("CRASH_ON_START", "Crash-on-start " + (enabled ? "enabled" : "disabled"),
                "Operator deliberately changed worker startup behavior.");
        store.save();
    }

    public synchronized void deployBadRelease() {
        NexusState state = store.snapshot();
        String version = "v" + (state.releases.size() + 1) + ".0.0";

        for (Release r : state.releases) {
            if ("CURRENT".equals(r.status)) r.status = "PREVIOUS";
        }

        Release bad = new Release(version, "CURRENT");
        state.releases.add(bad);
        state.currentRelease = version;

        ServiceStatus worker = state.services.get("Worker-01");
        worker.release = version;
        worker.crashOnStart = true;
        worker.status = "RESTARTING";

        Event event = new Event("RELEASE", "Bad release deployed: " + version,
                "Worker-01 startup behavior was changed by the release.");
        event.release = version;
        state.events.add(event);
        store.save();
    }

    public synchronized void rollback() {
        NexusState state = store.snapshot();
        Release previous = state.releases.stream()
                .filter(r -> "PREVIOUS".equals(r.status))
                .reduce((a, b) -> b)
                .orElse(null);

        if (previous == null) return;

        Release current = state.releases.stream()
                .filter(r -> "CURRENT".equals(r.status))
                .findFirst()
                .orElse(null);

        if (current != null) {
            current.status = "ROLLED_BACK";
            current.rolledBackAt = Instant.now();
        }

        previous.status = "CURRENT";
        state.currentRelease = previous.version;

        ServiceStatus worker = state.services.get("Worker-01");
        worker.release = previous.version;
        worker.crashOnStart = false;
        worker.status = "RUNNING";

        Event event = new Event("ROLLBACK", "Rolled back to " + previous.version,
                "The previous release was restored in one operator action.");
        event.release = previous.version;
        state.events.add(event);
        store.save();
    }

    public synchronized void duplicateLatest() {
        NexusState state = store.snapshot();
        WorkItem latest = state.work.values().stream()
                .max(Comparator.comparing(w -> w.createdAt))
                .orElse(null);

        if (latest == null) {
            store.addWork("demo", "{\"message\":\"duplicate-demo\"}", "JOB-DUP-1");
            latest = store.snapshot().work.get("JOB-DUP-1");
        }

        store.addWork(latest.type, latest.body, latest.id);
    }

    public synchronized void reset() {
        // Clear operational state by deleting and reloading through a fresh process state.
        // This method intentionally creates a safe local reset for demos.
        NexusState state = store.snapshot();
        state.work.clear();
        state.events.clear();
        state.releases.clear();
        state.currentRelease = "v1.0.0";
        state.nextJobNumber = 1001L;
        state.services.clear();
        state.services.put("Worker-01", new ServiceStatus("Worker-01"));
        state.services.put("Worker-02", new ServiceStatus("Worker-02"));
        state.services.put("Producer", new ServiceStatus("Producer"));
        state.releases.add(new Release("v1.0.0", "CURRENT"));
        store.addEvent("RESET", "NEXUS demo state reset", "Operator requested a clean demonstration state.");
        store.save();
    }
}
