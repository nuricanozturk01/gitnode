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
package dev.gitnode.os.shared.configs;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Caps the process-wide JGit pack window cache so native/heap use is bounded and documented instead
 * of relying on JGit defaults.
 */
@Slf4j
@Configuration
public class JGitWindowCacheConfig {

  private static final long MB = 1024L * 1024L;

  @Value("${gitnode.git.window-cache.packed-git-limit-mb:128}")
  private long packedGitLimitMb;

  @Value("${gitnode.git.window-cache.delta-base-cache-limit-mb:16}")
  private long deltaBaseCacheLimitMb;

  @PostConstruct
  void configure() {
    final WindowCacheConfig cfg = new WindowCacheConfig();
    cfg.setPackedGitLimit(this.packedGitLimitMb * MB);
    cfg.setDeltaBaseCacheLimit((int) (this.deltaBaseCacheLimitMb * MB));
    cfg.install();
    log.info(
        "JGit window cache: packedGitLimit={}MB, deltaBaseCacheLimit={}MB",
        this.packedGitLimitMb,
        this.deltaBaseCacheLimitMb);
  }
}
