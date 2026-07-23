package dev.gitnode.os.shared.registries.repsy.dtos;

public record RepsyUserCreateForm(
    String username,
    String password,
    String salt,
    String role) {}
