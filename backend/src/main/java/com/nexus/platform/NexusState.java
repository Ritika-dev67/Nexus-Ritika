package com.nexus.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NexusState {
    public Map<String, WorkItem> work = new LinkedHashMap<>();
    public Map<String, ServiceStatus> services = new LinkedHashMap<>();
    public List<Event> events = new ArrayList<>();
    public List<Release> releases = new ArrayList<>();
    public String currentRelease = "v1.0.0";
    public long nextJobNumber = 1001L;

    public NexusState() {
        services.put("Worker-01", new ServiceStatus("Worker-01"));
        services.put("Worker-02", new ServiceStatus("Worker-02"));
        services.put("Producer", new ServiceStatus("Producer"));
        releases.add(new Release("v1.0.0", "CURRENT"));
    }
}
