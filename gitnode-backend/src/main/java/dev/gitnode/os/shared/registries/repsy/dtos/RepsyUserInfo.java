package dev.gitnode.os.shared.registries.repsy.dtos;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

public record RepsyUserInfo(
    String id,
    String username,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAt,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastLoginAt) {}
