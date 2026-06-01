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
package com.nuricanozturk.originhub.pr.dtos;

import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record PrInfo(
    @NonNull UUID id,
    int number,
    @NonNull String title,
    @NonNull String status,
    boolean isDraft,
    @NonNull AuthorInfo author,
    @Nullable AuthorInfo mergedBy,
    @NonNull String sourceBranch,
    @NonNull String targetBranch,
    @Nullable String mergeSha,
    int commentCount,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt,
    @Nullable Instant mergedAt,
    @Nullable Instant closedAt) {}
