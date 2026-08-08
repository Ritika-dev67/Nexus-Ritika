package com.nexus.platform;

public class ServiceStatus {
    public String name;
    public String status;
    public int restartAttempts;
    public int maxRestartAttempts;
    public boolean slow;
    public boolean crashOnStart;
    public String release;

    public ServiceStatus() {}

    public ServiceStatus(String name) {
        this.name = name;
        this.status = "RUNNING";
        this.restartAttempts = 0;
        this.maxRestartAttempts = 3;
        this.slow = false;
        this.crashOnStart = false;
        this.release = "v1.0.0";
    }
}
