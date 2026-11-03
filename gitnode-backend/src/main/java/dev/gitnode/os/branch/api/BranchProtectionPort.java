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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

/** Branch protection as seen by other modules; the rule entity stays inside the branch module. */
@NullMarked
public interface BranchProtectionPort {

  /** Immutable view of the protection rule of one branch. */
  record BranchProtectionInfo(
      int requiredApprovals, boolean requireConversationResolution, List<String> requiredChecks) {}

  Optional<BranchProtectionInfo> findProtection(UUID repoId, String branch);

  /** Throws when the branch is protected and may only change through a merged pull request. */
  void assertDirectWriteAllowed(String owner, String repoName, String branch);
}
