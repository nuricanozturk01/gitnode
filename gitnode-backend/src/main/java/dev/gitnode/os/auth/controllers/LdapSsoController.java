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

import dev.gitnode.os.auth.dtos.LdapDiscoverResponse;
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
@RequestMapping("/api/auth/sso/ldap")
@ConditionalOnProperty(name = "gitnode.sso.ldap.enabled", havingValue = "true")
@RequiredArgsConstructor
@NullMarked
public class LdapSsoController {

  private final OrganizationService organizationService;

  @GetMapping("/discover")
  @RateLimit(limit = 20, windowSeconds = 60, key = "sso.ldap.discover")
  public ResponseEntity<LdapDiscoverResponse> discover(@RequestParam final String email) {

    final var result = this.organizationService.discoverLdapByEmail(email);

    if (result == null) {
      throw new ItemNotFoundException("ldapOrgNotFound");
    }

    return ResponseEntity.ok(result);
  }
}
