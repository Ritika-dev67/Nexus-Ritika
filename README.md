# NEXUS — Local Infrastructure Engineering Prototype

A small, local-only working model of NEXUS based on the engineering challenge handbook.

## What this prototype demonstrates

- Accepted work is persisted to a local state file.
- Work is processed by stand-in workers.
- Failed work is retried with a hard attempt limit.
- Work that exhausts retries becomes `GIVEN_UP`.
- Duplicate delivery is detected using the work ID.
- Worker failure can be triggered from the operator UI.
- Worker restart/recovery can be triggered from the operator UI.
- Slow worker simulation is supported.
- Crash-on-start simulation is supported.
- Bad release and one-click rollback are supported.
- An event timeline records what happened and why.
- The React operator console shows current health, work state, components, incidents, releases, and failure controls.
- Everything runs locally on one machine. No cloud services and no runtime AI calls are used.

## Requirements

- Java 17+
- Maven 3.9+ (or use the Maven wrapper if you add one)
- Node.js 20+

## Start backend

```bash
cd backend
mvn spring-boot:run
```

Backend: `http://localhost:8081`

## Start frontend

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

The frontend calls the backend at `http://localhost:8081/api`.

## Quick demo

1. Open the operator console.
2. Click **Create Test Work** several times.
3. Watch jobs move from WAITING → PROCESSING → COMPLETED.
4. Click **Kill Worker**.
5. Create more work. NEXUS keeps accepted work in its local state.
6. Click **Restart Worker**.
7. Watch the worker return to RUNNING and queued work continue.
8. Click **Crash on Start** and then restart the worker to demonstrate a bounded recovery attempt.
9. Click **Slow Worker** to increase processing delay.
10. Click **Duplicate Latest Job** to demonstrate duplicate detection.
11. Click **Deploy Bad Release**, then **Rollback Release**.
12. Review the timeline to see the sequence and reason for each action.

## Important implementation note

This is intentionally a small model, not a rebuild of dozens of production services. The worker and producer are stand-in components represented inside the local Spring Boot process so the reviewer can deliberately break them and observe recovery.

The state is stored in `nexus-state.json` in the backend working directory. This gives accepted work a durable local representation without requiring a database or cloud service.

## Core requirements targeted

- R-01: durable accepted work
- R-02: every accepted work item reaches COMPLETED or GIVEN_UP
- R-03: duplicate delivery is explicitly detected/ignored
- R-04: bounded processing and restart attempts
- R-05: recent history is visible in the operator timeline
- R-06: release rollback is a single operator action
- R-07: release events and subsequent worker behavior share the same event timeline

## Failure controls intentionally implemented

- Kill worker
- Crash on startup
- Slow worker
- Duplicate job
- Bad release
- Rollback release

## Not implemented

This prototype does not attempt every extended requirement from the handbook. It is sized as a six-hour/small-machine demonstration model.
