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

package dev.gitnode.os.actions.adapters;

import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.shared.branch.RequiredChecksPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RequiredChecksAdapter implements RequiredChecksPort {

  private final WorkflowRunRepository runs;

  @Override
  @Transactional(readOnly = true)
  public Map<String, String> states(
      final UUID repoId, final String sha, final Collection<String> workflowNames) {
    final var result = new LinkedHashMap<String, String>();
    for (final var name : workflowNames) {
      result.put(
          name,
          this.runs
              .findFirstByRepoIdAndTriggerShaAndWorkflowNameOrderByCreatedAtDesc(repoId, sha, name)
              .map(run -> run.getStatus().name())
              .orElse("MISSING"));
    }
    return result;
  }
}
