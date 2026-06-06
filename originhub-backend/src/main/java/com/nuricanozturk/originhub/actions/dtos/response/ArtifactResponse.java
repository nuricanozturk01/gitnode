/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nuricanozturk.originhub.actions.dtos.response;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ArtifactResponse(
    UUID id,
    String name,
    @Nullable Long sizeBytes,
    @Nullable String contentType,
    @Nullable Instant expiresAt,
    Instant createdAt) {}
