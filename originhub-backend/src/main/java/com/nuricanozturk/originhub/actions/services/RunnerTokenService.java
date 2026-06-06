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
package com.nuricanozturk.originhub.actions.services;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

/** Issues and validates runner-specific JWTs, separate from user tokens. */
@Slf4j
@Service
@NullMarked
public class RunnerTokenService {

  private static final String RUNNER_TYPE_CLAIM = "runnerType";
  private static final String RUNNER_TYPE_VALUE = "runner";
  private static final String ISSUER = "originhub-runner";
  private static final int EXPIRATION_HOURS = 8760; // 1 year

  @Value("${originhub.jwt.secret}")
  private String secret;

  public String generateRunnerToken(final UUID runnerId) {

    final var now = new Date();

    return Jwts.builder()
        .header()
        .type(OAuth2AccessToken.TokenType.BEARER.getValue())
        .and()
        .id(UUID.randomUUID().toString())
        .subject(runnerId.toString())
        .issuer(ISSUER)
        .issuedAt(now)
        .expiration(DateUtils.addHours(now, EXPIRATION_HOURS))
        .claims(Map.of(RUNNER_TYPE_CLAIM, RUNNER_TYPE_VALUE))
        .signWith(this.signingKey())
        .compact();
  }

  public UUID extractRunnerId(final String token) {

    try {
      final var claims = this.parseClaims(token);

      if (!RUNNER_TYPE_VALUE.equals(claims.get(RUNNER_TYPE_CLAIM))) {
        throw new TokenExpiredException("invalidRunnerToken");
      }

      return UUID.fromString(claims.getSubject());
    } catch (final JwtException ex) {
      log.debug("Runner token rejected: {}", ex.getMessage());
      throw new TokenExpiredException("invalidRunnerToken");
    }
  }

  public void validate(final String token) {

    try {
      final var claims = this.parseClaims(token);

      if (!RUNNER_TYPE_VALUE.equals(claims.get(RUNNER_TYPE_CLAIM))) {
        throw new TokenExpiredException("invalidRunnerToken");
      }
    } catch (final JwtException ex) {
      log.debug("Runner token validation failed: {}", ex.getMessage());
      throw new TokenExpiredException("invalidRunnerToken");
    }
  }

  private Claims parseClaims(final String token) {

    return Jwts.parser()
        .verifyWith(this.signingKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private SecretKey signingKey() {

    return Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(this.secret));
  }
}
