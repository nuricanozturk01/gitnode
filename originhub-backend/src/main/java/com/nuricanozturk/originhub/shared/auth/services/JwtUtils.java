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
package com.nuricanozturk.originhub.shared.auth.services;

import static com.nuricanozturk.originhub.shared.auth.utils.BearerTokenUtils.getJwtToken;
import static com.nuricanozturk.originhub.shared.auth.utils.BearerTokenUtils.isBearerToken;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.TokenExpiredException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.jspecify.annotations.NullMarked;
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
  private static final String ISSUER = "originhub-application";
  private static final String EMAIL = "email";

  @Value("${originhub.jwt.secret}")
  private String secret;

  private SecretKey getSigningKey() {
    final var keyBytes = java.util.Base64.getDecoder().decode(this.secret);

    return Keys.hmacShaKeyFor(keyBytes);
  }

  public UUID extractUserId(final String token) {

    try {
      final var jwt = isBearerToken(token) ? getJwtToken(token) : token;
      return UUID.fromString(this.extractClaim(jwt, Claims::getSubject));
    } catch (final JwtException ex) {
      throw new TokenExpiredException("invalidToken");
    }
  }

  private <T> T extractClaim(final String token, final Function<Claims, T> claimsResolver) {
    final Claims claims = this.extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  private Claims extractAllClaims(final String token) {
    return Jwts.parser()
        .verifyWith(this.getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String generateToken(final Tenant user) {

    final var claims = Map.of(EMAIL, user.getEmail());

    return this.createToken(claims, user.getId(), ACCESS_EXPIRATION_HOUR);
  }

  public String generateRefreshToken(final Tenant user) {

    final var claims = Map.of(EMAIL, user.getEmail());

    return this.createToken(claims, user.getId(), REFRESH_EXPIRATION_HOUR);
  }

  private String createToken(
      final Map<String, String> claims, final UUID subject, final int expiration) {

    final var currentTimeMillis = System.currentTimeMillis();

    final var tokenIssuedAt = new Date(currentTimeMillis);

    final var accessTokenExpiresAt = DateUtils.addHours(new Date(currentTimeMillis), expiration);

    return Jwts.builder()
        .header()
        .type(OAuth2AccessToken.TokenType.BEARER.getValue())
        .and()
        .id(UUID.randomUUID().toString())
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
      Jwts.parser().verifyWith(this.getSigningKey()).build().parseSignedClaims(jwt);
    } catch (final JwtException e) {
      log.info("Token rejected: {}", e.getMessage());
      throw new TokenExpiredException("invalidToken");
    }
  }
}
