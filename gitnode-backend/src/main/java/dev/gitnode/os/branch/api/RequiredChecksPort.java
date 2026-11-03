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

package dev.gitnode.os.branch.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Reports which required CI workflows have not succeeded for a commit. */
public interface RequiredChecksPort {

  /**
   * Returns the names from {@code workflowNames} whose latest run for {@code sha} is missing or has
   * not finished successfully.
   */
  default List<String> notPassing(
      final UUID repoId, final String sha, final Collection<String> workflowNames) {
    return this.states(repoId, sha, workflowNames).entrySet().stream()
        .filter(entry -> !"SUCCESS".equals(entry.getValue()))
        .map(java.util.Map.Entry::getKey)
        .toList();
  }

  /**
   * Latest run status per workflow name ({@code SUCCESS}, {@code FAILURE}, ... or {@code MISSING}).
   */
  java.util.Map<String, String> states(UUID repoId, String sha, Collection<String> workflowNames);
}
