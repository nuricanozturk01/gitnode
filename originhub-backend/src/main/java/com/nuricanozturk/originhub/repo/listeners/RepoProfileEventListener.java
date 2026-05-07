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

import com.nuricanozturk.originhub.shared.profile.events.TenantDeletedEvent;
import com.nuricanozturk.originhub.shared.profile.events.UsernameChangedEvent;
import com.nuricanozturk.originhub.shared.repo.services.RepoStorageService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class RepoProfileEventListener {

  private final @NonNull RepoStorageService repoStorageService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUsernameChanged(final @NonNull UsernameChangedEvent event) {

    this.repoStorageService.renameBaseDir(event.oldUsername(), event.newUsername());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTenantDeleted(final @NonNull TenantDeletedEvent event) {

    this.repoStorageService.deleteTenantRepos(event.username());
  }
}
