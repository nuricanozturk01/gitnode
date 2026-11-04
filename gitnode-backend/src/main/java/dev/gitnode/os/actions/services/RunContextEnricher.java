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

import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.identity.api.TenantQueryPort;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adds what a job needs to know about who and what triggered its run: {@code github.actor}, {@code
 * github.head_ref}, {@code github.run_attempt}, server URLs and the {@code github.event} payload
 * with its sender.
 */
@Component
@RequiredArgsConstructor
@NullMarked
public class RunContextEnricher {

  private final TenantQueryPort tenants;

  @Value("${gitnode.actions.server-url:}")
  private String serverUrl = "";

  /** Extra {@code github.*} expression values for the run. */
  public Map<String, String> contextFor(final WorkflowRun run) {

    final var ctx = new LinkedHashMap<String, String>();
    ctx.put("github.run_attempt", String.valueOf(run.getRunAttempt()));
    final var actor = this.actorName(run);
    if (actor != null) {
      ctx.put("github.actor", actor);
      ctx.put("github.triggering_actor", actor);
    }
    if ("pull_request".equals(run.getTriggerEvent())) {
      ctx.put("github.head_ref", branch(run.getTriggerRef()));
      final var base = nested(run.getEventPayload(), "pull_request", "base", "ref");
      if (base != null) {
        ctx.put("github.base_ref", base);
      }
    }
    if (!this.serverUrl.isBlank()) {
      final var url = this.serverUrl.replaceAll("/+$", "");
      ctx.put("github.server_url", url);
      ctx.put("github.api_url", url + "/api");
    }
    return ctx;
  }

  /** The event payload with {@code sender}, or null when the run has none. */
  public @Nullable Map<String, Object> eventFor(final WorkflowRun run) {

    final var stored = run.getEventPayload();
    if (stored == null) {
      return null;
    }
    final var event = new LinkedHashMap<String, Object>(stored);
    final var actor = this.actorName(run);
    if (actor != null) {
      event.put("sender", Map.of("login", actor));
    }
    return event;
  }

  private @Nullable String actorName(final WorkflowRun run) {
    if (run.getTriggerActor() == null) {
      return null;
    }
    return this.tenants
        .findById(run.getTriggerActor())
        .map(TenantQueryPort.TenantRef::username)
        .orElse(null);
  }

  private static String branch(final @Nullable String ref) {
    return ref == null ? "" : ref.replaceFirst("^refs/heads/", "");
  }

  @SuppressWarnings("unchecked")
  private static @Nullable String nested(
      final @Nullable Map<String, Object> root, final String... path) {
    Object current = root;
    for (final var key : path) {
      if (!(current instanceof final Map<?, ?> map)) {
        return null;
      }
      current = ((Map<String, Object>) map).get(key);
    }
    return current instanceof final String text && !text.isEmpty() ? text : null;
  }
}
