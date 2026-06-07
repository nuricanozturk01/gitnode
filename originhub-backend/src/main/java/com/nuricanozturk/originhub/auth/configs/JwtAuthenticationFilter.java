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
package com.nuricanozturk.originhub.auth.configs;

import static com.nuricanozturk.originhub.shared.auth.utils.BearerTokenUtils.getJwtToken;
import static com.nuricanozturk.originhub.shared.auth.utils.BearerTokenUtils.isBearerToken;

import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.TokenExpiredException;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@RequiredArgsConstructor
@NullMarked
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtils jwtUtils;
  private final TenantRepository tenantRepository;

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (!isBearerToken(authHeader)) {
      filterChain.doFilter(request, response);
      return;
    }

    final var jwt = getJwtToken(authHeader);

    try {
      this.jwtUtils.verifyAndValidate(jwt);
    } catch (final TokenExpiredException e) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("\"" + e.getMessage() + "\"");
      return;
    }

    final var tenantId = this.jwtUtils.extractUserId(jwt);

    final var userOpt = this.tenantRepository.findById(tenantId);
    if (userOpt.isEmpty()) {
      response.setStatus(HttpStatus.NOT_FOUND.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("\"userNotFound\"");
      return;
    }

    final var user = userOpt.get();

    if (!user.isEnabled()) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("\"userDisabled\"");
      return;
    }

    final var authentication = new UsernamePasswordAuthenticationToken(user, null, null);

    SecurityContextHolder.getContext().setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    return request.getRequestURI().startsWith("/ws/");
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }
}
