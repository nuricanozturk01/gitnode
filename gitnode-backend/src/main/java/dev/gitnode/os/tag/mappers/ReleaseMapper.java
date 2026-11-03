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
package dev.gitnode.os.tag.mappers;

import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.tag.dtos.ReleaseInfo;
import dev.gitnode.os.tag.dtos.TenantReleaseInfo;
import dev.gitnode.os.tag.entities.Release;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/** Maps a release plus its (separately looked up) author; the release only stores the author id. */
@Component
@NullMarked
public class ReleaseMapper {

  /** Used when the author account no longer resolves (deleted concurrently). */
  private static TenantReleaseInfo ghost(final java.util.UUID id) {
    return new TenantReleaseInfo(id, "ghost", null, null);
  }

  public ReleaseInfo toInfo(final Release release, final TenantReleaseInfo author) {
    return ReleaseInfo.builder()
        .id(release.getId())
        .tagName(release.getTagName())
        .name(release.getName())
        .body(release.getBody())
        .isDraft(release.isDraft())
        .isPrerelease(release.isPrerelease())
        .isLatest(false)
        .author(author)
        .publishedAt(release.getPublishedAt())
        .createdAt(release.getCreatedAt())
        .updatedAt(release.getUpdatedAt())
        .build();
  }

  public TenantReleaseInfo toTenantInfo(final TenantRef tenant) {
    return new TenantReleaseInfo(
        tenant.id(), tenant.username(), tenant.displayName(), tenant.avatarUrl());
  }

  public TenantReleaseInfo toTenantInfoOrGhost(
      final java.util.UUID authorId,
      final java.util.@org.jspecify.annotations.Nullable Map<java.util.UUID, TenantReleaseInfo>
          known) {
    final var found = known == null ? null : known.get(authorId);
    return found != null ? found : ghost(authorId);
  }
}
