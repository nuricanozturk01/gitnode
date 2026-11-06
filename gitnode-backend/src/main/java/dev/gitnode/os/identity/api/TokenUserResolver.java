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
import org.jspecify.annotations.Nullable;

/** Resolves the authenticated user id from a bearer token / raw access token. */
public interface TokenUserResolver {

  /**
   * Returns the user id of a valid access token.
   *
   * @throws RuntimeException (token expired / invalid) when the token cannot be verified
   */
  UUID extractUserId(String token);

  /** Like {@link #extractUserId} but returns {@code null} for missing or invalid tokens. */
  @Nullable UUID tryExtractUserId(@Nullable String token);
}
