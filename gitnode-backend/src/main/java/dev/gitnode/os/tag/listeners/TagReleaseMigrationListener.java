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
package dev.gitnode.os.tag.listeners;

import dev.gitnode.os.events.tag.TagReleaseMigrationRequestedEvent;
import dev.gitnode.os.tag.dtos.CreateReleaseForm;
import dev.gitnode.os.tag.dtos.CreateTagForm;
import dev.gitnode.os.tag.services.ReleaseTxService;
import dev.gitnode.os.tag.services.TagNonTxService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class TagReleaseMigrationListener {

  private static final String GITHUB_API_BASE = "https://api.github.com";
  private static final String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";
  private static final String GITHUB_API_VERSION = "2022-11-28";

  private final ReleaseTxService releaseTxService;
  private final TagNonTxService tagNonTxService;
  private final RestClient restClient;

  @EventListener
  public void onMigrateTagsRequested(final TagReleaseMigrationRequestedEvent event) {

    final var owner = event.getRemoteRepoOwner();
    final var repoName = event.getRemoteRepoName();

    this.migrateReleases(event, owner, repoName);
  }

  private void migrateReleases(
      final TagReleaseMigrationRequestedEvent event, final String owner, final String repoName) {

    final var releases =
        this.restClient
            .get()
            .uri(GITHUB_API_BASE + "/repos/{owner}/{repo}/releases?per_page=100", owner, repoName)
            .header("Authorization", "Bearer " + event.getAccessToken())
            .header(GITHUB_API_VERSION_HEADER, GITHUB_API_VERSION)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    if (releases == null || releases.isEmpty()) {
      return;
    }

    final var tenantUsername = event.getTenantUsername();

    releases.parallelStream()
        .forEach(release -> this.createRelease(release, event, tenantUsername, repoName));
  }

  private void createRelease(
      final Map<String, Object> release,
      final TagReleaseMigrationRequestedEvent event,
      final String tenantUsername,
      final String repoName) {

    final var tagName = (String) release.get("tag_name");
    if (tagName == null || tagName.isBlank()) {
      return;
    }

    try {
      this.ensureTagExists(tenantUsername, repoName, tagName, release);

      final var form = this.buildReleaseForm(release, tagName);
      this.releaseTxService.create(tenantUsername, repoName, event.getTenantId(), form);
    } catch (final Exception e) {
      log.debug("Release migrate edilemedi: {} - {}", tagName, e.getMessage());
    }
  }

  private void ensureTagExists(
      final String tenantUsername,
      final String repoName,
      final String tagName,
      final Map<String, Object> release)
      throws java.io.IOException {

    try {
      this.tagNonTxService.get(tenantUsername, repoName, tagName);
    } catch (final Exception _) {
      final var tagBody = (String) release.getOrDefault("tag_name", tagName);
      final var tagMsg = (String) release.get("body");
      log.debug("Tag {} bulunamadı, oluşturuluyor...", tagBody);

      final var tagForm = new CreateTagForm();
      tagForm.setName(tagBody);
      if (tagMsg != null && !tagMsg.isBlank()) {
        tagForm.setMessage(tagMsg);
      }
      this.tagNonTxService.create(tenantUsername, repoName, tagForm);
    }
  }

  private CreateReleaseForm buildReleaseForm(
      final Map<String, Object> release, final String tagName) {

    final var form = new CreateReleaseForm();
    form.setTagName(tagName);
    form.setName((String) release.get("name"));
    form.setBody((String) release.get("body"));
    form.setDraft(Boolean.TRUE.equals(release.get("draft")));
    form.setPrerelease(Boolean.TRUE.equals(release.get("prerelease")));
    form.setCreateNewTag(false);
    return form;
  }
}
