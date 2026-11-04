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
package dev.gitnode.os.team.api;

import static dev.gitnode.os.repo.api.CollaboratorPermission.ACTIONS_WRITE;
import static dev.gitnode.os.repo.api.CollaboratorPermission.ISSUE_MANAGE;
import static dev.gitnode.os.repo.api.CollaboratorPermission.PULL_REQUEST_CREATE;
import static dev.gitnode.os.repo.api.CollaboratorPermission.PULL_REQUEST_MERGE;
import static dev.gitnode.os.repo.api.CollaboratorPermission.PULL_REQUEST_REVIEW;
import static dev.gitnode.os.repo.api.CollaboratorPermission.PUSH;
import static dev.gitnode.os.repo.api.CollaboratorPermission.READ;
import static dev.gitnode.os.repo.api.CollaboratorPermission.SETTINGS_READ;
import static dev.gitnode.os.repo.api.CollaboratorPermission.SETTINGS_WRITE;

import dev.gitnode.os.repo.api.CollaboratorPermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public enum TeamRole {
  VIEWER(EnumSet.of(READ)),
  DEVELOPER(
      EnumSet.of(
          READ, PUSH, PULL_REQUEST_CREATE, PULL_REQUEST_REVIEW, ISSUE_MANAGE, SETTINGS_READ)),
  MAINTAINER(
      EnumSet.of(
          READ,
          PUSH,
          PULL_REQUEST_CREATE,
          PULL_REQUEST_REVIEW,
          ISSUE_MANAGE,
          SETTINGS_READ,
          PULL_REQUEST_MERGE,
          ACTIONS_WRITE,
          SETTINGS_WRITE)),
  ADMIN(EnumSet.allOf(CollaboratorPermission.class));

  private final Set<CollaboratorPermission> permissions;

  TeamRole(final EnumSet<CollaboratorPermission> permissions) {
    this.permissions = Collections.unmodifiableSet(permissions);
  }

  public Set<CollaboratorPermission> permissions() {
    return this.permissions;
  }

  public boolean grants(final CollaboratorPermission permission) {
    return this == ADMIN || this.permissions.contains(permission);
  }
}
