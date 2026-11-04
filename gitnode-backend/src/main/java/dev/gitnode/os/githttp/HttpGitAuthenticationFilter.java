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
package dev.gitnode.os.githttp;

import dev.gitnode.os.githttp.api.RunnerTokenPort;
import dev.gitnode.os.identity.api.GitCredentialPort;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.GitAccessPort;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Slf4j
@NullMarked
public class HttpGitAuthenticationFilter implements Filter {

  private static final String RUNNER_USERNAME = "x-token";

  private static final String RECEIVE_PACK = "git-receive-pack";

  private final GitCredentialPort credentials;
  private final GitAccessPort gitAccess;
  private final TenantQueryPort tenants;
  @Nullable private final RunnerTokenPort runnerTokenPort;

  public HttpGitAuthenticationFilter(
      final GitCredentialPort credentials,
      final GitAccessPort gitAccess,
      final TenantQueryPort tenants,
      final @Nullable RunnerTokenPort runnerTokenPort) {
    this.credentials = credentials;
    this.gitAccess = gitAccess;
    this.tenants = tenants;
    this.runnerTokenPort = runnerTokenPort;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {

    final var httpRequest = (HttpServletRequest) request;
    final var httpResponse = (HttpServletResponse) response;
    final var isWrite = isWriteRequest(httpRequest);
    final var repoPath = extractOwnerAndRepo(httpRequest);

    try {
      final var auth = httpRequest.getHeader("Authorization");

      if (auth != null && auth.startsWith("Basic ")) {
        final var username = this.authenticate(auth);
        final var status = this.authorize(username, repoPath, isWrite);
        if (status == HttpServletResponse.SC_OK) {
          chain.doFilter(new AuthenticatedRequest(httpRequest, username), response);
        } else {
          sendError(httpResponse, status);
        }
      } else if (this.isPublicRead(repoPath, isWrite)) {
        chain.doFilter(request, response);
      } else {
        this.sendUnauthorized(httpResponse, "Unauthorized");
      }
    } catch (final IllegalArgumentException ex) {
      this.sendUnauthorized(httpResponse, "Unauthorized: " + ex.getMessage());
    }
  }

  private boolean isPublicRead(final Optional<String[]> repoPath, final boolean isWrite) {
    return !isWrite
        && repoPath.isPresent()
        && this.gitAccess.canRead(null, repoPath.get()[0], repoPath.get()[1]);
  }

  private int authorize(
      final String username, final Optional<String[]> repoPath, final boolean isWrite) {

    if (RUNNER_USERNAME.equals(username)) {
      return isWrite ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_OK;
    }

    if (repoPath.isEmpty()) {
      return HttpServletResponse.SC_NOT_FOUND;
    }

    final var owner = repoPath.get()[0];
    final var repoName = repoPath.get()[1];
    final var tenantId =
        this.tenants
            .findByUsername(username)
            .map(TenantRef::id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (!this.gitAccess.canRead(tenantId, owner, repoName)) {
      log.info("HTTP Git access denied: {} cannot read {}/{}", username, owner, repoName);
      return HttpServletResponse.SC_NOT_FOUND;
    }

    if (isWrite && !this.gitAccess.canWrite(tenantId, owner, repoName)) {
      log.info("HTTP Git access denied: {} cannot push to {}/{}", username, owner, repoName);
      return HttpServletResponse.SC_FORBIDDEN;
    }

    return HttpServletResponse.SC_OK;
  }

  private static boolean isWriteRequest(final HttpServletRequest request) {
    final var queryString = request.getQueryString();
    return decodedPath(request).contains(RECEIVE_PACK)
        || request.getRequestURI().contains(RECEIVE_PACK)
        || RECEIVE_PACK.equals(request.getParameter("service"))
        || queryString != null && queryString.contains(RECEIVE_PACK);
  }

  private static String decodedPath(final HttpServletRequest request) {
    final var pathInfo = request.getPathInfo();
    return pathInfo != null ? pathInfo : "";
  }

  private static Optional<String[]> extractOwnerAndRepo(final HttpServletRequest request) {
    final var path = decodedPath(request);
    final var segments = (path.startsWith("/") ? path.substring(1) : path).split("/");

    if (segments.length < 2 || segments[0].isBlank() || segments[1].isBlank()) {
      return Optional.empty();
    }

    final var owner = segments[0];
    final var repo =
        segments[1].endsWith(".git")
            ? segments[1].substring(0, segments[1].length() - 4)
            : segments[1];

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

      this.credentials.verifyPassword(username, password);

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

  private static void sendError(final HttpServletResponse response, final int status)
      throws IOException {
    response.setStatus(status);
    response.setContentType("text/plain");
    response
        .getWriter()
        .write(status == HttpServletResponse.SC_FORBIDDEN ? "Forbidden" : "Repository not found");
    response.getWriter().flush();
  }

  private void sendUnauthorized(final HttpServletResponse response, final String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader("WWW-Authenticate", "Basic realm=\"GitNode\"");
    response.setContentType("text/plain");
    response.getWriter().write(message);
    response.getWriter().flush();
  }
}
