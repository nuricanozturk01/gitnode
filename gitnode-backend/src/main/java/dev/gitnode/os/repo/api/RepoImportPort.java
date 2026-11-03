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
package dev.gitnode.os.repo.api;

import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

/** Imports an existing remote repository as a new repository owned by an account. */
@NullMarked
public interface RepoImportPort {

  /**
   * Mirrors {@code githubOwner/repoName} from GitHub into the account's namespace and registers it.
   * Cleans up the partially imported repository when the import fails.
   */
  void importFromGithub(String repoName, String githubOwner, UUID tenantId, String accessToken)
      throws IOException;
}
