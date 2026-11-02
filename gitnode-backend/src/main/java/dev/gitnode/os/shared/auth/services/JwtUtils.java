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
package dev.gitnode.os.shared.auth.services;

import static dev.gitnode.os.shared.auth.utils.BearerTokenUtils.getJwtToken;
import static dev.gitnode.os.shared.auth.utils.BearerTokenUtils.isBearerToken;

import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
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
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
public class JwtUtils {

  public static final int ACCESS_EXPIRATION_SECONDS = 24 * 3600;
  public static final int REFRESH_EXPIRATION_SECONDS = 48 * 3600;

  private static final int ACCESS_EXPIRATION_HOUR = 24;
  private static final int REFRESH_EXPIRATION_HOUR = 48;
  private static final String ISSUER = "gitnode-application";
  private static final String EMAIL = "email";
  private static final String TOKEN_USE = "token_use";
  private static final String ACCESS = "access";
  private static final String REFRESH = "refresh";
  private static final String FAMILY = "fam";

  @Value("${gitnode.jwt.secret}")
  private String secret;

  private SecretKey getSigningKey() {
    final var keyBytes = java.util.Base64.getDecoder().decode(this.secret);

    return Keys.hmacShaKeyFor(keyBytes);
  }

  public UUID extractUserId(final String token) {

    try {
      final var jwt = isBearerToken(token) ? getJwtToken(token) : token;
      return UUID.fromString(this.extractClaims(jwt, ACCESS).getSubject());
    } catch (final JwtException | IllegalArgumentException ex) {
      throw new TokenExpiredException("invalidToken");
    }
  }

  public @Nullable UUID tryExtractUserId(final @Nullable String token) {

    if (token == null || token.isBlank()) {
      return null;
    }

    try {
      return this.extractUserId(token);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private Claims extractClaims(final String token, final String tokenUse) {
    return Jwts.parser()
        .verifyWith(this.getSigningKey())
        .requireIssuer(ISSUER)
        .require(TOKEN_USE, tokenUse)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String generateToken(final Tenant user) {

    final var claims = Map.of(EMAIL, user.getEmail(), TOKEN_USE, ACCESS);

    return this.createToken(claims, user.getId(), ACCESS_EXPIRATION_HOUR);
  }

  /** Identity of a refresh token: who it belongs to, its own id and the rotation family. */
  public record RefreshClaims(UUID userId, UUID tokenId, UUID familyId) {}

  public String generateRefreshToken(final Tenant user, final UUID tokenId, final UUID familyId) {

    final var claims =
        Map.of(EMAIL, user.getEmail(), TOKEN_USE, REFRESH, FAMILY, familyId.toString());

    return this.createToken(claims, user.getId(), REFRESH_EXPIRATION_HOUR, tokenId);
  }

  public RefreshClaims extractRefreshClaims(final String token) {
    try {
      final var claims = this.extractClaims(token, REFRESH);
      return new RefreshClaims(
          UUID.fromString(claims.getSubject()),
          UUID.fromString(claims.getId()),
          UUID.fromString(claims.get(FAMILY, String.class)));
    } catch (final JwtException | IllegalArgumentException | NullPointerException ex) {
      throw new TokenExpiredException("invalidToken");
    }
  }

  private String createToken(
      final Map<String, String> claims, final UUID subject, final int expiration) {
    return this.createToken(claims, subject, expiration, UUID.randomUUID());
  }

  private String createToken(
      final Map<String, String> claims,
      final UUID subject,
      final int expiration,
      final UUID tokenId) {

    final var currentTimeMillis = System.currentTimeMillis();

    final var tokenIssuedAt = new Date(currentTimeMillis);

    final var accessTokenExpiresAt = DateUtils.addHours(new Date(currentTimeMillis), expiration);

    return Jwts.builder()
        .header()
        .type(OAuth2AccessToken.TokenType.BEARER.getValue())
        .and()
        .id(tokenId.toString())
        .subject(subject.toString())
        .issuer(ISSUER)
        .issuedAt(tokenIssuedAt)
        .expiration(accessTokenExpiresAt)
        .signWith(this.getSigningKey())
        .claims(claims)
        .compact();
  }

  public void verifyAndValidate(final String jwt) {

    try {
      this.extractClaims(jwt, ACCESS);
    } catch (final JwtException e) {
      log.info("Token rejected: {}", e.getMessage());
      throw new TokenExpiredException("invalidToken");
    }
  }
}
