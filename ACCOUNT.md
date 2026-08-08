# NEXUS Submission Account

## Implementation choices

### Platform
Spring Boot / Java 17. The platform owns work state, retry decisions, worker state, releases, rollback, and the event timeline.

### Persistence
A local JSON state file (`nexus-state.json`) is used instead of a hosted database. This keeps the prototype single-machine and easy to inspect.

### Operator view
React + Vite. The UI reads the platform state through REST endpoints and exposes the deliberate failure controls reviewers need.

### Stand-in services
The producer and workers are modeled as local components inside the Spring Boot process. This keeps the demonstration small while still allowing deliberate kill, slow, crash-on-start, duplicate, release, and rollback scenarios.

## Determinism

The platform makes decisions from saved state and explicit operator actions. Failure modes are controlled by flags in the saved state rather than by hidden randomness.

## Retry policy

Work has a maximum of 3 processing attempts. Worker restart attempts are also limited to 3. When work reaches its processing limit it becomes `GIVEN_UP`. When a worker reaches its restart limit it becomes `OUT_OF_SERVICE`.

## Failure scenarios demonstrated

1. Kill worker during processing.
2. Restart worker.
3. Crash on startup.
4. Slow worker.
5. Duplicate work delivery.
6. Bad release.
7. One-action rollback.

## Known scope limits

This is a small local engineering model. It does not attempt every extended requirement from the handbook, multi-machine coordination, external dependency orchestration, or production-scale throughput.
