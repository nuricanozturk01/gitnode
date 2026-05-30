package com.nuricanozturk.originhub.shared.issue.events;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record IssueDeletedEvent(UUID issueId) {}
