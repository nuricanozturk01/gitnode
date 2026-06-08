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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@NullMarked
public class ActionsWebSocketConfig implements WebSocketConfigurer {

  private final RunnerWebSocketHandler runnerWebSocketHandler;
  private final RunnerHandshakeInterceptor handshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
    registry
        .addHandler(this.runnerWebSocketHandler, "/ws/runner/**")
        .addInterceptors(this.handshakeInterceptor)
        .setAllowedOriginPatterns("*");
  }
}
