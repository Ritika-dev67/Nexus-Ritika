package com.nexus.platform;

import java.time.Instant;

public class Event {
    public String id;
    public Instant timestamp;
    public String type;
    public String message;
    public String reason;
    public String relatedWorkId;
    public String release;

    public Event() {}

    public Event(String type, String message, String reason) {
        this.id = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.type = type;
        this.message = message;
        this.reason = reason;
    }
}
