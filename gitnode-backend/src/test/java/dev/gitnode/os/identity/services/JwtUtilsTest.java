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
package dev.gitnode.os.identity.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilsTest {

  private final JwtUtils jwtUtils = new JwtUtils();
  private final Tenant tenant = new Tenant();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        jwtUtils,
        "secret",
        Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
    tenant.setId(UUID.randomUUID());
    tenant.setEmail("alice@example.com");
  }

  @Test
  void accessAndRefreshTokensAreRestrictedToTheirPurpose() {
    String access = jwtUtils.generateToken(tenant);
    UUID tokenId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String refresh = jwtUtils.generateRefreshToken(tenant, tokenId, familyId);

    jwtUtils.verifyAndValidate(access);
    assertThat(jwtUtils.extractUserId(access)).isEqualTo(tenant.getId());
    assertThat(jwtUtils.extractRefreshClaims(refresh))
        .isEqualTo(new JwtUtils.RefreshClaims(tenant.getId(), tokenId, familyId));
    assertThatThrownBy(() -> jwtUtils.verifyAndValidate(refresh))
        .isInstanceOf(TokenExpiredException.class);
    assertThatThrownBy(() -> jwtUtils.extractUserId(refresh))
        .isInstanceOf(TokenExpiredException.class);
    assertThatThrownBy(() -> jwtUtils.extractRefreshClaims(access))
        .isInstanceOf(TokenExpiredException.class);
  }
}
