package com.nexus.platform;

import java.time.Instant;

public class Release {
    public String version;
    public String status;
    public Instant deployedAt;
    public Instant rolledBackAt;

    public Release() {}

    public Release(String version, String status) {
        this.version = version;
        this.status = status;
        this.deployedAt = Instant.now();
    }
}
