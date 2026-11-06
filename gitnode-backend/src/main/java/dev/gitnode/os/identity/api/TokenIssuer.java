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
package dev.gitnode.os.identity.api;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

/** Issues and validates signed tokens. */
@NullMarked
public interface TokenIssuer {

  int ACCESS_EXPIRATION_SECONDS = 24 * 3600;
  int REFRESH_EXPIRATION_SECONDS = 48 * 3600;

  /** Identity of a refresh token: who it belongs to, its own id and the rotation family. */
  record RefreshClaims(UUID userId, UUID tokenId, UUID familyId) {}

  String generateToken(TenantAccount user);

  String generateRefreshToken(TenantAccount user, UUID tokenId, UUID familyId);

  RefreshClaims extractRefreshClaims(String token);

  void verifyAndValidate(String jwt);
}
