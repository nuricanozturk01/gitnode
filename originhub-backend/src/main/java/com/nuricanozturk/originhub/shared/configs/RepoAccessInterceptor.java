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
package com.nuricanozturk.originhub.shared.configs;

import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RepoAccessInterceptor implements HandlerInterceptor {

  private final @NonNull RepoRepository repoRepository;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull JwtUtils jwtUtils;

  @Override
  public boolean preHandle(
      final @NonNull HttpServletRequest request,
      final @NonNull HttpServletResponse response,
      final @NonNull Object handler)
      throws IOException {

    if (!HttpMethod.GET.matches(request.getMethod())) {
      return true;
    }

    final var repo = this.resolvePrivateRepo(request);
    if (repo == null) {
      return true;
    }

    return this.checkAuthorization(request, response, repo);
  }

  private Repo resolvePrivateRepo(final HttpServletRequest request) {

    final var parsed = parseOwnerRepo(request.getRequestURI());

    return parsed
        .flatMap(
            strings ->
                this.repoRepository
                    .findByOwnerUsernameAndName(strings[0], strings[1])
                    .filter(Repo::isPrivate))
        .orElse(null);
  }

  private boolean checkAuthorization(
      final HttpServletRequest request, final HttpServletResponse response, final Repo repo)
      throws IOException {

    final var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return this.writeForbidden(response);
    }

    try {
      final var requesterId = this.jwtUtils.extractUserId(authHeader);
      final boolean isOwner = repo.getOwner().getId().equals(requesterId);
      final boolean isAdmin =
          this.tenantRepository.findById(requesterId).map(Tenant::isAdmin).orElse(false);

      return isOwner || isAdmin || this.writeForbidden(response);
    } catch (final Exception _) {
      return this.writeUnauthorized(response);
    }
  }

  private boolean writeForbidden(final HttpServletResponse response) throws IOException {
    this.writeError(response, HttpServletResponse.SC_FORBIDDEN, "repoAccessDenied");
    return false;
  }

  private boolean writeUnauthorized(final HttpServletResponse response) throws IOException {
    this.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalidToken");
    return false;
  }

  private void writeError(final HttpServletResponse response, final int status, final String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"" + code + "\"}");
  }

  private static Optional<String[]> parseOwnerRepo(final String uri) {
    final var prefix = "/api/repos/";

    if (!uri.startsWith(prefix)) {
      return Optional.empty();
    }

    final var rest = uri.substring(prefix.length());
    final var slash = rest.indexOf('/');

    if (slash < 0) {
      return Optional.empty();
    }

    final var owner = rest.substring(0, slash);
    final var afterOwner = rest.substring(slash + 1);
    final var slash2 = afterOwner.indexOf('/');
    final var repoName = slash2 < 0 ? afterOwner : afterOwner.substring(0, slash2);

    if (owner.isBlank() || repoName.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(new String[] {owner, repoName});
  }
}
