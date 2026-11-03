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
package dev.gitnode.os.repo.api;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Authorization questions about a repository, answered for other modules. */
@NullMarked
public interface RepoAccessPort {

  /** Read access: public repositories for everyone, private ones for owner and collaborators. */
  void assertUserCanAccessRepo(@Nullable UUID tenantId, String owner, String repoName);

  void assertIsRepoOwner(UUID tenantId, String owner, String repoName);

  void assertUserHasPermission(
      UUID tenantId, String owner, String repoName, CollaboratorPermission permission);

  void assertUserHasAnyPermission(
      UUID tenantId, String owner, String repoName, CollaboratorPermission... permissions);
}
