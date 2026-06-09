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
package dev.gitnode.os.repo.listeners;

import dev.gitnode.os.events.profile.TenantDeletedEvent;
import dev.gitnode.os.events.profile.UsernameChangedEvent;
import dev.gitnode.os.shared.repo.services.RepoStorageService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class RepoProfileEventListener {

  private final RepoStorageService repoStorageService;

  @ApplicationModuleListener
  public void onUsernameChanged(final UsernameChangedEvent event) {

    this.repoStorageService.renameBaseDir(event.oldUsername(), event.newUsername());
  }

  @ApplicationModuleListener
  public void onTenantDeleted(final TenantDeletedEvent event) {

    this.repoStorageService.deleteTenantRepos(event.username());
  }
}
