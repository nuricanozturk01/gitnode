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
package com.nuricanozturk.originhub.http.services;

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpGitAuthenticator {

  private final @NonNull TenantRepository tenantRepository;

  public @NonNull Tenant authenticate(final @NonNull HttpServletRequest request) {
    final var auth = request.getHeader("Authorization");

    if (auth != null && auth.startsWith("Basic ")) {
      return authenticateBasic(auth);
    }

    final var principal = SecurityContextHolder.getContext().getAuthentication();
    if (principal != null && principal.isAuthenticated()) {
      return authenticateSession(principal);
    }

    throw new IllegalArgumentException("Unauthorized: missing or invalid credentials");
  }

  private @NonNull Tenant authenticateBasic(final @NonNull String authHeader) {
    try {
      final var base64 = authHeader.substring("Basic ".length());
      final var decoded = new String(Base64.getDecoder().decode(base64));
      final var parts = decoded.split(":", 2);

      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid Basic auth format");
      }

      final var username = parts[0];
      final var password = parts[1];

      final var tenant = this.tenantRepository.findByUsername(username)
          .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

      validatePassword(tenant, password);

      log.info("HTTP Basic auth success: username={}", username);
      return tenant;
    } catch (final IllegalArgumentException ex) {
      log.warn("HTTP Basic auth failed: {}", ex.getMessage());
      throw ex;
    }
  }

  private void validatePassword(final @NonNull Tenant tenant, final @NonNull String password) {
    if (tenant.getSalt() == null || tenant.getHash() == null) {
      throw new IllegalArgumentException("User password not configured");
    }

    final var hash = DigestUtils.sha256Hex(password + tenant.getSalt());

    if (!hash.equals(tenant.getHash())) {
      throw new IllegalArgumentException("Invalid password");
    }
  }

  private @NonNull Tenant authenticateSession(
      final org.springframework.security.core.Authentication principal) {
    final var name = principal.getName();

    final var tenant = this.tenantRepository.findByUsername(name)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + name));

    log.info("HTTP session auth success: username={}", name);
    return tenant;
  }
}
