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
package dev.gitnode.os.shared.collaborator.services;

import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface CollaboratorAccessPort {

  boolean isActiveCollaborator(UUID repoId, UUID tenantId);

  boolean hasPendingInvitation(UUID repoId, UUID tenantId);

  boolean hasPermission(UUID repoId, UUID tenantId, CollaboratorPermission permission);

  Set<UUID> findAcceptedRepoIds(UUID tenantId, String ownerUsername);
}
