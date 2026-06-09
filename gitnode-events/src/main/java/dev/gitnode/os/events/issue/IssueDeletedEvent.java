package dev.gitnode.os.events.issue;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record IssueDeletedEvent(UUID issueId) {}
