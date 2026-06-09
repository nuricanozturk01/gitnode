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
package dev.gitnode.os.actions.websocket;

import dev.gitnode.os.actions.services.RunnerTokenService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class RunnerHandshakeInterceptor implements HandshakeInterceptor {

  static final String ATTR_RUNNER_ID = "runnerId";
  static final String ATTR_TOKEN = "runnerToken";

  private final RunnerTokenService runnerTokenService;

  @Override
  public boolean beforeHandshake(
      final ServerHttpRequest request,
      final ServerHttpResponse response,
      final WebSocketHandler wsHandler,
      final Map<String, Object> attributes) {

    final var token = this.extractBearerToken(request);

    if (token == null) {
      log.debug("WebSocket connection rejected: missing Authorization header");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    try {
      this.runnerTokenService.validate(token);
      final var runnerId = this.runnerTokenService.extractRunnerId(token);
      attributes.put(ATTR_RUNNER_ID, runnerId);
      attributes.put(ATTR_TOKEN, token);
      return true;
    } catch (final Exception ex) {
      log.debug("WebSocket connection rejected: invalid token — {}", ex.getMessage());
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      final ServerHttpRequest request,
      final ServerHttpResponse response,
      final WebSocketHandler wsHandler,
      final @Nullable Exception exception) {}

  private @Nullable String extractBearerToken(final ServerHttpRequest request) {
    final var bearer = "Bearer ";
    final var auth = request.getHeaders().getFirst("Authorization");
    if (auth != null && auth.startsWith(bearer)) {
      return auth.substring(bearer.length());
    }
    return null;
  }
}
