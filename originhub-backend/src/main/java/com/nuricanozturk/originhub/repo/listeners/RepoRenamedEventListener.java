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
package com.nuricanozturk.originhub.repo.listeners;

import com.nuricanozturk.originhub.repo.services.RepoStorageService;
import com.nuricanozturk.originhub.shared.repo.events.RepoRenameRollbackRequstedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoRenamedEvent;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RepoRenamedEventListener {

  private final @NonNull RepoStorageService repoStorageService;
  private final @NonNull RepoService repoService;

  @Async
  @EventListener
  public void onRepoNameChanged(final @NonNull RepoRenamedEvent event) {

    this.repoStorageService.renameRepo(event.repoOwner(), event.oldRepoName(), event.newRepoName());
  }

  @Async
  @EventListener
  public void onRepoRenameIOExceptionOccurred(
      final @NonNull RepoRenameRollbackRequstedEvent event) {

    this.repoService.rollbackRepoName(event.owner(), event.oldRepoName(), event.newRepoName());
  }
}
