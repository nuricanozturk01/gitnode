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
package com.nuricanozturk.originhub.shared.git.http;

import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Slf4j
@NullMarked
public class HttpGitAuthenticationFilter implements Filter {

  private static final String RUNNER_USERNAME = "x-token";

  private final TenantRepository tenantRepository;
  private final RepoRepository repoRepository;
  @Nullable private final RunnerTokenPort runnerTokenPort;

  public HttpGitAuthenticationFilter(
      final TenantRepository tenantRepository,
      final RepoRepository repoRepository,
      final @Nullable RunnerTokenPort runnerTokenPort) {
    this.tenantRepository = tenantRepository;
    this.repoRepository = repoRepository;
    this.runnerTokenPort = runnerTokenPort;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {

    final var httpRequest = (HttpServletRequest) request;
    final var httpResponse = (HttpServletResponse) response;

    try {
      final var auth = httpRequest.getHeader("Authorization");

      if (auth != null && auth.startsWith("Basic ")) {
        final var username = this.authenticate(auth);
        chain.doFilter(new AuthenticatedRequest(httpRequest, username), response);
      } else if (this.isPublicReadRequest(httpRequest)) {
        chain.doFilter(request, response);
      } else {
        this.sendUnauthorized(httpResponse, "Unauthorized");
      }
    } catch (final IllegalArgumentException ex) {
      this.sendUnauthorized(httpResponse, "Unauthorized: " + ex.getMessage());
    }
  }

  private boolean isPublicReadRequest(final HttpServletRequest request) {
    final var uri = request.getRequestURI();
    final var queryString = request.getQueryString();
    final var isWriteRequest =
        uri.contains("git-receive-pack")
            || (queryString != null && queryString.contains("service=git-receive-pack"));

    if (isWriteRequest) {
      return false;
    }

    final var partsOpt = this.extractOwnerAndRepo(uri);
    if (partsOpt.isEmpty()) {
      return false;
    }

    final var parts = partsOpt.get();

    final var repoOpt = this.repoRepository.findByOwnerUsernameAndName(parts[0], parts[1]);
    return repoOpt.isPresent() && !repoOpt.get().isPrivate();
  }

  private Optional<String[]> extractOwnerAndRepo(final String uri) {
    final var gitPrefix = "/git/";

    if (!uri.startsWith(gitPrefix)) {
      return Optional.empty();
    }

    final var segments = uri.substring(gitPrefix.length()).split("/");

    if (segments.length < 2) {
      return Optional.empty();
    }

    final var owner = segments[0];
    final var repo = segments[1].replace(".git", "");

    return Optional.of(new String[] {owner, repo});
  }

  private static String[] decodeCredentials(final String authHeader) {
    final var base64 = authHeader.substring("Basic ".length());
    final var decoded = new String(Base64.getDecoder().decode(base64));
    final var parts = decoded.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid Basic auth format");
    }
    return new String[] {parts[0].toLowerCase(Locale.getDefault()), parts[1]};
  }

  private String authenticate(final String authHeader) {
    try {
      final var credentials = decodeCredentials(authHeader);
      final var username = credentials[0];
      final var password = credentials[1];

      if (RUNNER_USERNAME.equals(username)) {
        return this.authenticateRunner(password);
      }

      final var tenant =
          this.tenantRepository
              .findByUsername(username)
              .orElseThrow(() -> new IllegalArgumentException("User not found"));

      if (tenant.getSalt() == null || tenant.getHash() == null) {
        throw new IllegalArgumentException("User password not configured");
      }

      final var hash = DigestUtils.sha256Hex(password + tenant.getSalt());

      if (!hash.equals(tenant.getHash())) {
        throw new IllegalArgumentException("Invalid password");
      }

      log.info("HTTP Git auth success: {}", username);
      return username;
    } catch (final IllegalArgumentException ex) {
      throw ex;
    } catch (final Exception ex) {
      throw new IllegalArgumentException("Auth failed: " + ex.getMessage(), ex);
    }
  }

  private String authenticateRunner(final String token) {
    if (this.runnerTokenPort == null) {
      throw new IllegalArgumentException("Runner authentication not available");
    }
    if (!this.runnerTokenPort.isValid(token)) {
      throw new IllegalArgumentException("Invalid runner token");
    }
    log.info("HTTP Git auth success: runner");
    return RUNNER_USERNAME;
  }

  private void sendUnauthorized(final HttpServletResponse response, final String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader("WWW-Authenticate", "Basic realm=\"OriginHub\"");
    response.setContentType("text/plain");
    response.getWriter().write(message);
    response.getWriter().flush();
  }
}
