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

import dev.gitnode.os.auth.dtos.SamlDiscoverResponse;
import dev.gitnode.os.auth.services.OrganizationService;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/sso/saml")
@ConditionalOnProperty(name = "gitnode.sso.saml.enabled", havingValue = "true")
@RequiredArgsConstructor
@NullMarked
public class SamlSsoController {

  private final OrganizationService organizationService;

  /**
   * Resolves the SAML registration for a user email domain.
   *
   * <p>Frontend flow: call this with the user's email, then redirect to {@code redirectUrl} from
   * the response.
   */
  @GetMapping("/discover")
  @RateLimit(limit = 20, windowSeconds = 60, key = "sso.saml.discover")
  public ResponseEntity<SamlDiscoverResponse> discover(@RequestParam final String email) {

    final var result = this.organizationService.discoverByEmail(email);

    if (result == null) {
      throw new ItemNotFoundException("samlOrgNotFound");
    }

    return ResponseEntity.ok(result);
  }
}
