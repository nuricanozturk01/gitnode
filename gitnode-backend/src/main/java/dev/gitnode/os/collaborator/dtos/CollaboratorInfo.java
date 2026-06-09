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
package dev.gitnode.os.collaborator.dtos;

import dev.gitnode.os.collaborator.entities.CollaboratorStatus;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CollaboratorInfo {
  private final UUID id;
  private final String username;
  private final String displayName;
  private final String avatarUrl;
  private final Set<CollaboratorPermission> permissions;
  private final CollaboratorStatus status;
  private final String invitedBy;
  private final Instant createdAt;
  private final Instant updatedAt;
}
