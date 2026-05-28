package com.nuricanozturk.originhub.shared.issue.events;

import java.util.UUID;
import org.jspecify.annotations.NonNull;

public record IssueDeletedEvent(@NonNull UUID issueId) {}
