package com.nuricanozturk.originhub.events.issue;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record IssueDeletedEvent(UUID issueId) {}
