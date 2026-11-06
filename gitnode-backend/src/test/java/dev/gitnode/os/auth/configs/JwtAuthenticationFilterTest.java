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
package dev.gitnode.os.auth.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import dev.gitnode.os.identity.api.TenantAccountStore;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

  private JwtUtils jwtUtils;
  private FilterChain chain;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    this.jwtUtils = mock(JwtUtils.class);
    this.chain = mock(FilterChain.class);
    this.filter = new JwtAuthenticationFilter(this.jwtUtils, mock(TenantAccountStore.class));
    doThrow(new TokenExpiredException("invalidToken"))
        .when(this.jwtUtils)
        .verifyAndValidate("runner-jwt");
  }

  private MockHttpServletResponse call(final String method, final String uri) throws Exception {
    final var request = new MockHttpServletRequest(method, uri);
    request.addHeader("Authorization", "Bearer runner-jwt");
    final var response = new MockHttpServletResponse();
    this.filter.doFilter(request, response, this.chain);
    return response;
  }

  @Test
  @DisplayName("a runner token on the artifact endpoints is left to the endpoint to check")
  void runnerTokenReachesArtifactEndpoints() throws Exception {
    final var upload = this.call("POST", "/api/actions/artifacts/upload");
    final var download = this.call("GET", "/api/actions/artifacts/run-id/out");
    final var cache = this.call("GET", "/api/actions/cache");

    assertThat(upload.getStatus()).isEqualTo(200);
    assertThat(download.getStatus()).isEqualTo(200);
    assertThat(cache.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("an invalid token anywhere else is still a 401")
  void invalidTokenElsewhereIsRefused() throws Exception {
    final var response = this.call("GET", "/api/repos/alice/app/actions/runs");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("invalidToken");
  }
}
