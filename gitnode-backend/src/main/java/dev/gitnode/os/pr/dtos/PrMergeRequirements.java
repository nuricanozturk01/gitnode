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

package dev.gitnode.os.pr.dtos;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/** What the target branch rule still needs before this pull request can merge. */
@NullMarked
public record PrMergeRequirements(
    boolean protectedBranch,
    int requiredApprovals,
    int currentApprovals,
    List<String> changesRequestedBy,
    List<Check> checks,
    boolean requireConversationResolution,
    int unresolvedConversations,
    boolean satisfied,
    boolean canBypass) {

  /** {@code state} is the latest run status for the source head, or {@code MISSING}. */
  public record Check(String name, String state) {}
}
