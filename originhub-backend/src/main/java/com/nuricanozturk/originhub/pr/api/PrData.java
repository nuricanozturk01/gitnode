package com.nuricanozturk.originhub.pr.api;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PrData(
    UUID id, int number, String title, String sourceBranch, String targetBranch, String status) {}
