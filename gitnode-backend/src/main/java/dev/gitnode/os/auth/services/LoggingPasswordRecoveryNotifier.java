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

package dev.gitnode.os.auth.services;

import dev.gitnode.os.identity.entities.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder until mail delivery is configured: records that a request happened without ever
 * logging the secret code.
 */
@Slf4j
@Component
public class LoggingPasswordRecoveryNotifier implements PasswordRecoveryNotifier {

  @Override
  public void send(final Tenant tenant, final String code) {
    log.warn(
        "Password recovery requested for user {} but no mail delivery is configured; the code was"
            + " not sent",
        tenant.getUsername());
  }
}
