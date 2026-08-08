import { useCallback, useEffect, useMemo, useState } from "react";

const API = "http://localhost:8081/api";

function statusClass(status) {
  if (status === "RUNNING") return "running";
  if (status === "DOWN" || status === "OUT_OF_SERVICE") return "down";
  if (status === "RESTARTING") return "restarting";
  return "neutral";
}

function formatTime(value) {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
}

function formatAge(value) {
  if (!value) return "—";
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
}

export default function Dashboard() {
  const [state, setState] = useState(null);
  const [events, setEvents] = useState([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [slowEnabled, setSlowEnabled] = useState(false);
  const [crashEnabled, setCrashEnabled] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [stateRes, eventRes] = await Promise.all([
        fetch(`${API}/state`),
        fetch(`${API}/events?limit=20`)
      ]);
      if (!stateRes.ok || !eventRes.ok) throw new Error("Backend unavailable");
      setState(await stateRes.json());
      setEvents(await eventRes.json());
    } catch {
      setMessage("Backend is offline. Start Spring Boot on port 8081.");
    }
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 1000);
    return () => clearInterval(timer);
  }, [refresh]);

  async function action(path, options = {}) {
    setBusy(true);
    setMessage("");
    try {
      const response = await fetch(`${API}${path}`, {
        method: "POST",
        ...options
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Action failed");
      setMessage(data.message || "Action completed");
      await refresh();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function createWork() {
    await action("/work", {
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        type: "order",
        body: JSON.stringify({
          orderId: `demo-${Date.now()}`,
          source: "operator-console"
        })
      })
    });
  }

  const workItems = state ? Object.values(state.work) : [];
  const services = state ? Object.values(state.services) : [];

  const counts = useMemo(() => ({
    waiting: workItems.filter(w => w.status === "WAITING").length,
    processing: workItems.filter(w => w.status === "PROCESSING").length,
    completed: workItems.filter(w => w.status === "COMPLETED").length,
    givenUp: workItems.filter(w => w.status === "GIVEN_UP").length,
    duplicates: workItems.filter(w => w.duplicate).length
  }), [workItems]);

  const oldest = workItems
    .filter(w => w.status === "WAITING")
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))[0];

  const worker = services.find(s => s.name === "Worker-01");
  const degraded = services.some(s => s.status !== "RUNNING") || counts.givenUp > 0;

  return (
    <div className="app">
      <header className="topbar">
        <div>
          <div className="brand">NEXUS</div>
          <div className="subtitle">Operator Console</div>
        </div>
        <div className={`system-pill ${degraded ? "bad" : "good"}`}>
          {degraded ? "⚠ SYSTEM DEGRADED" : "● SYSTEM HEALTHY"}
        </div>
      </header>

      <main className="container">
        {message && <div className="toast">{message}</div>}

        <section className={`incident ${degraded ? "incident-bad" : "incident-good"}`}>
          <div className="incident-title">
            <span className="incident-icon">{degraded ? "🚨" : "✅"}</span>
            <div>
              <h2>{degraded ? "Active Incident" : "No Active Incident"}</h2>
              <p>
                {degraded
                  ? `${worker?.name || "Worker"} is ${worker?.status || "unknown"} and NEXUS is tracking recovery.`
                  : "All components are running and accepted work is flowing normally."}
              </p>
            </div>
          </div>

          <div className="incident-grid">
            <Metric label="Started" value={events.at(-1) ? formatTime(events.at(-1).timestamp) : "—"} />
            <Metric label="Waiting Work" value={counts.waiting} />
            <Metric label="Oldest Work" value={oldest ? formatAge(oldest.createdAt) : "—"} />
            <Metric label="Restart Attempts" value={`${worker?.restartAttempts ?? 0}/${worker?.maxRestartAttempts ?? 3}`} />
          </div>
        </section>

        <section>
          <SectionTitle title="Work" />
          <div className="stats-grid">
            <Stat title="Waiting" value={counts.waiting} />
            <Stat title="Processing" value={counts.processing} />
            <Stat title="Completed" value={counts.completed} />
            <Stat title="Given Up" value={counts.givenUp} />
            <Stat title="Duplicates" value={counts.duplicates} />
          </div>
        </section>

        <section>
          <SectionTitle title="Components" />
          <div className="panel">
            {services.map(service => (
              <div className="component-row" key={service.name}>
                <div>
                  <strong>{service.name}</strong>
                  <span>
                    Restart attempts: {service.restartAttempts}/{service.maxRestartAttempts}
                    {service.release ? ` · ${service.release}` : ""}
                  </span>
                </div>
                <div className="component-right">
                  <span className={`status ${statusClass(service.status)}`}>
                    ● {service.status}
                  </span>
                  {service.slow && <span className="mini-tag">SLOW</span>}
                  {service.crashOnStart && <span className="mini-tag danger">CRASH-ON-START</span>}
                </div>
              </div>
            ))}
          </div>
        </section>

        <div className="two-column">
          <section>
            <SectionTitle title="Incident Timeline" />
            <div className="panel timeline">
              {events.length === 0 ? (
                <div className="empty">No events yet.</div>
              ) : (
                events.map(event => (
                  <div className="event" key={event.id}>
                    <div className="event-time">{formatTime(event.timestamp)}</div>
                    <div>
                      <strong>{event.message}</strong>
                      <p>{event.reason}</p>
                      {event.release && <span className="release-link">Release {event.release}</span>}
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>

          <section>
            <SectionTitle title="Failure Lab" />
            <div className="panel failure-lab">
              <p className="help">
                Deliberately trigger failures so a reviewer can observe NEXUS behavior.
              </p>

              <div className="button-grid">
                <button className="danger-btn" disabled={busy} onClick={() => action("/chaos/kill-worker")}>
                  Kill Worker
                </button>

                <button
                  className={crashEnabled ? "active-btn" : "danger-btn"}
                  disabled={busy}
                  onClick={async () => {
                    const next = !crashEnabled;
                    setCrashEnabled(next);
                    await action(`/chaos/crash-on-start?enabled=${next}`);
                  }}
                >
                  {crashEnabled ? "Disable Crash-on-Start" : "Crash on Start"}
                </button>

                <button
                  className="warning-btn"
                  disabled={busy}
                  onClick={async () => {
                    const next = !slowEnabled;
                    setSlowEnabled(next);
                    await action(`/chaos/slow-worker?enabled=${next}`);
                  }}
                >
                  {slowEnabled ? "Disable Slow Worker" : "Slow Worker"}
                </button>

                <button
                  className="warning-btn"
                  disabled={busy}
                  onClick={() => action("/chaos/duplicate-latest")}
                >
                  Duplicate Latest Job
                </button>

                <button className="primary-btn" disabled={busy} onClick={() => action("/chaos/restart-worker")}>
                  Restart Worker
                </button>

                <button className="primary-btn" disabled={busy} onClick={createWork}>
                  Create Test Work
                </button>

                <button className="release-btn" disabled={busy} onClick={() => action("/releases/bad")}>
                  Deploy Bad Release
                </button>

                <button className="release-btn" disabled={busy} onClick={() => action("/releases/rollback")}>
                  Rollback Release
                </button>

                <button className="reset-btn" disabled={busy} onClick={() => action("/reset")}>
                  Reset Demo
                </button>
              </div>
            </div>
          </section>
        </div>

        <section>
          <SectionTitle title="Releases" />
          <div className="panel releases">
            {(state?.releases || []).slice().reverse().map(release => (
              <div className="release-row" key={release.version}>
                <div>
                  <strong>{release.version}</strong>
                  <span>Deployed {formatTime(release.deployedAt)}</span>
                </div>
                <span className={`status ${release.status === "CURRENT" ? "running" : "neutral"}`}>
                  ● {release.status}
                </span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function SectionTitle({ title }) {
  return <h2 className="section-title">{title}</h2>;
}

function Metric({ label, value }) {
  return (
    <div>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}

function Stat({ title, value }) {
  return (
    <div className="stat-card">
      <div className="stat-label">{title}</div>
      <div className="stat-value">{value}</div>
    </div>
  );
}
