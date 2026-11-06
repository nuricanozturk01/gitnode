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
/**
 * Git-backed wiki: pages are markdown files on the {@code refs/wiki/main} branch of the repository
 * itself, so the wiki follows the repository through renames, forks and deletion.
 */
@NullMarked
@ApplicationModule(
    allowedDependencies = {
      "identity :: api",
      "repo :: api",
      "repo :: dtos",
      "shared :: cache",
      "shared :: errorhandling-exceptions",
      "shared :: git-provider",
      "shared :: ratelimit"
    })
package dev.gitnode.os.wiki;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
