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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.auth.entities.RefreshToken;
import dev.gitnode.os.auth.repositories.RefreshTokenRepository;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

  private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
  private final JwtUtils jwtUtils = new JwtUtils();
  private final Map<UUID, RefreshToken> store = new HashMap<>();
  private RefreshTokenService service;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        jwtUtils,
        "secret",
        Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
    tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setEmail("alice@example.com");
    when(repository.save(any(RefreshToken.class)))
        .thenAnswer(
            invocation -> {
              RefreshToken saved = invocation.getArgument(0);
              store.put(saved.getId(), saved);
              return saved;
            });
    when(repository.findById(any(UUID.class)))
        .thenAnswer(
            invocation ->
                java.util.Optional.ofNullable(store.get(invocation.<UUID>getArgument(0))));
    when(repository.claim(any(UUID.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              RefreshToken token = store.get(invocation.<UUID>getArgument(0));
              if (token.getUsedAt() != null || token.getRevokedAt() != null) {
                return 0;
              }
              token.setUsedAt(invocation.getArgument(1));
              return 1;
            });
    when(repository.revokeFamily(any(UUID.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              store.values().stream()
                  .filter(t -> t.getFamilyId().equals(invocation.getArgument(0)))
                  .forEach(t -> t.setRevokedAt(invocation.getArgument(1)));
              return 1;
            });
    service = new RefreshTokenService(repository, jwtUtils);
  }

  private RefreshTokenService.Rotation rotate(final String token) {
    return service.rotate(token, id -> tenant);
  }

  @Test
  @DisplayName("each refresh token works once and yields a successor in the same family")
  void rotatesOnce() {
    final var first = service.issue(tenant);

    final var second = rotate(first).refreshToken();

    assertThat(second).isNotEqualTo(first);
    assertThat(jwtUtils.extractRefreshClaims(second).familyId())
        .isEqualTo(jwtUtils.extractRefreshClaims(first).familyId());
    assertThat(rotate(second).refreshToken()).isNotBlank();
  }

  @Test
  @DisplayName("replaying an old token well after use revokes the whole family")
  void reuseRevokesFamily() {
    final var first = service.issue(tenant);
    final var second = rotate(first).refreshToken();
    store
        .get(jwtUtils.extractRefreshClaims(first).tokenId())
        .setUsedAt(Instant.now().minusSeconds(120));

    assertThatThrownBy(() -> rotate(first)).isInstanceOf(TokenExpiredException.class);

    assertThatThrownBy(() -> rotate(second)).isInstanceOf(TokenExpiredException.class);
  }

  @Test
  @DisplayName("a reuse inside the race window is rejected without revoking the family")
  void raceWindowDoesNotRevoke() {
    final var first = service.issue(tenant);
    final var second = rotate(first).refreshToken();

    assertThatThrownBy(() -> rotate(first)).isInstanceOf(TokenExpiredException.class);

    assertThat(rotate(second).refreshToken()).isNotBlank();
  }

  @Test
  @DisplayName("logout revokes the family so the token no longer works")
  void revokeStopsTheFamily() {
    final var token = service.issue(tenant);

    service.revoke(token);

    assertThatThrownBy(() -> rotate(token)).isInstanceOf(TokenExpiredException.class);
  }

  @Test
  @DisplayName("tokens that were never issued by the server are rejected")
  void unknownTokenRejected() {
    final var forged = jwtUtils.generateRefreshToken(tenant, UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> rotate(forged)).isInstanceOf(TokenExpiredException.class);
    verify(repository, never()).claim(any(), any());
  }

  @Test
  @DisplayName("access tokens and garbage cannot be used as refresh tokens")
  void wrongTokenTypeRejected() {
    assertThatThrownBy(() -> rotate(jwtUtils.generateToken(tenant)))
        .isInstanceOf(TokenExpiredException.class);
    assertThatThrownBy(() -> rotate("not-a-jwt")).isInstanceOf(TokenExpiredException.class);
    service.revoke("not-a-jwt");
    verify(repository, never()).revokeFamily(any(), any());
  }
}
