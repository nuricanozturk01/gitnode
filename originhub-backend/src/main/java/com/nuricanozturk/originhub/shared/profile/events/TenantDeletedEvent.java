package com.nuricanozturk.originhub.shared.profile.events;

import org.jspecify.annotations.NonNull;

public record TenantDeletedEvent(@NonNull String username) {}
