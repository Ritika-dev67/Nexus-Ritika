package com.nexus.platform;

import java.time.Instant;

public class WorkItem {
    public String id;
    public String type;
    public String body;
    public String status;
    public int attempts;
    public Instant createdAt;
    public Instant updatedAt;
    public String worker;
    public boolean duplicate;
    public String lastError;

    public WorkItem() {}

    public WorkItem(String id, String type, String body) {
        this.id = id;
        this.type = type;
        this.body = body;
        this.status = "WAITING";
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.worker = null;
        this.duplicate = false;
        this.lastError = null;
    }
}
