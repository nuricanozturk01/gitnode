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
package dev.gitnode.os.auth.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SsoProvidersControllerTest {

  @Test
  void reportsTheConfiguredProviders() {
    final var body = new SsoProvidersController(true, false).providers().getBody();

    assertThat(body).isNotNull();
    assertThat(body.ldap()).isTrue();
    assertThat(body.saml()).isFalse();
  }

  @Test
  void reportsNothingWhenBothAreOff() {
    final var body = new SsoProvidersController(false, false).providers().getBody();

    assertThat(body).isNotNull();
    assertThat(body.ldap()).isFalse();
    assertThat(body.saml()).isFalse();
  }
}
