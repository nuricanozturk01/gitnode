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

import dev.gitnode.os.auth.dtos.SsoProvidersResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always registered, unlike the LDAP/SAML controllers that only exist when their feature is
 * enabled, so the login page can ask which options to offer.
 */
@RestController
@RequestMapping("/api/auth/sso")
@NullMarked
class SsoProvidersController {

  private final boolean ldapEnabled;
  private final boolean samlEnabled;

  SsoProvidersController(
      @Value("${gitnode.sso.ldap.enabled:false}") final boolean ldapEnabled,
      @Value("${gitnode.sso.saml.enabled:false}") final boolean samlEnabled) {
    this.ldapEnabled = ldapEnabled;
    this.samlEnabled = samlEnabled;
  }

  @GetMapping("/providers")
  public ResponseEntity<SsoProvidersResponse> providers() {
    return ResponseEntity.ok(new SsoProvidersResponse(this.ldapEnabled, this.samlEnabled));
  }
}
