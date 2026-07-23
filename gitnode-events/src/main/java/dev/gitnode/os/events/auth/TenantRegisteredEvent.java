package dev.gitnode.os.events.auth;

public record TenantRegisteredEvent(String username, String hash, String salt) {}
