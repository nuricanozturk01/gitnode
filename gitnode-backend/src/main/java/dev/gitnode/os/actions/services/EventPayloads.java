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
package dev.gitnode.os.actions.services;

import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The webhook-style payload GitHub exposes as {@code github.event} and {@code $GITHUB_EVENT_PATH},
 * built when a run is queued. Who triggered it is added when a job is dispatched.
 */
@NullMarked
final class EventPayloads {

  /** GitHub's "no previous commit" sha, used as {@code before} when it is unknown. */
  static final String ZERO_SHA = "0000000000000000000000000000000000000000";

  private EventPayloads() {}

  static Map<String, Object> build(
      final String event,
      final RepoRef repo,
      final String ref,
      final @Nullable String sha,
      final @Nullable String commitMessage,
      final @Nullable String baseBranch,
      final @Nullable Map<String, String> inputs) {

    final var payload = new LinkedHashMap<String, Object>();
    payload.put("repository", repository(repo));
    switch (event) {
      case "push" -> addPush(payload, ref, sha, commitMessage);
      case "pull_request" -> addPullRequest(payload, ref, sha, baseBranch);
      case "workflow_dispatch" -> {
        payload.put("ref", ref);
        payload.put("inputs", inputs == null ? Map.of() : inputs);
      }
      case "schedule" -> payload.put("ref", ref);
      default -> payload.put("ref", ref);
    }
    return payload;
  }

  private static Map<String, Object> repository(final RepoRef repo) {
    final var map = new LinkedHashMap<String, Object>();
    map.put("name", repo.name());
    map.put("full_name", repo.ownerUsername() + "/" + repo.name());
    map.put("private", repo.isPrivate());
    map.put("default_branch", repo.defaultBranch() == null ? "main" : repo.defaultBranch());
    map.put("owner", Map.of("login", repo.ownerUsername()));
    return map;
  }

  private static void addPush(
      final Map<String, Object> payload,
      final String ref,
      final @Nullable String sha,
      final @Nullable String commitMessage) {

    payload.put("ref", ref);
    payload.put("before", ZERO_SHA);
    payload.put("after", sha == null ? ZERO_SHA : sha);
    if (sha != null) {
      final var commit = new LinkedHashMap<String, Object>();
      commit.put("id", sha);
      commit.put("message", commitMessage == null ? "" : commitMessage);
      payload.put("head_commit", commit);
      payload.put("commits", List.of(commit));
    }
  }

  private static void addPullRequest(
      final Map<String, Object> payload,
      final String ref,
      final @Nullable String sha,
      final @Nullable String baseBranch) {

    final var branch = ref.replaceFirst("^refs/heads/", "");
    final var head = new LinkedHashMap<String, Object>();
    head.put("ref", branch);
    head.put("sha", sha == null ? "" : sha);
    final var base = new LinkedHashMap<String, Object>();
    base.put("ref", baseBranch == null ? "" : baseBranch);
    final var pullRequest = new LinkedHashMap<String, Object>();
    pullRequest.put("head", head);
    pullRequest.put("base", base);
    payload.put("action", "synchronize");
    payload.put("pull_request", pullRequest);
  }
}
