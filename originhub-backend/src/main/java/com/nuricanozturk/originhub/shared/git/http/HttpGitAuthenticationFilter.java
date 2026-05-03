package com.nuricanozturk.originhub.shared.git.http;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
@RequiredArgsConstructor
public class HttpGitAuthenticationFilter implements Filter {

  private final TenantRepository tenantRepository;

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
      } else {
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"OriginHub\"");
        httpResponse.setContentType("text/plain");
        httpResponse.getWriter().write("Unauthorized");
        httpResponse.getWriter().flush();
      }
    } catch (final IllegalArgumentException ex) {
      httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"OriginHub\"");
      httpResponse.setContentType("text/plain");
      httpResponse.getWriter().write("Unauthorized: " + ex.getMessage());
      httpResponse.getWriter().flush();
    }
  }

  private String authenticate(final String authHeader) {
    try {
      final var base64 = authHeader.substring("Basic ".length());
      final var decoded = new String(Base64.getDecoder().decode(base64));
      final var parts = decoded.split(":", 2);

      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid Basic auth format");
      }

      final var username = parts[0].toLowerCase(Locale.getDefault());
      final var password = parts[1];

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
}
