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
package dev.gitnode.os.events.repo;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A tag was created or moved by a push. {@code beforeSha} is the all-zero sha for a new tag. */
@NullMarked
public record RepoTagPushedEvent(
    UUID repoId,
    String tagName,
    String pusherUsername,
    @Nullable String commitSha,
    @Nullable String beforeSha) {

  public RepoTagPushedEvent(
      final UUID repoId,
      final String tagName,
      final String pusherUsername,
      final @Nullable String commitSha) {
    this(repoId, tagName, pusherUsername, commitSha, null);
  }
}
