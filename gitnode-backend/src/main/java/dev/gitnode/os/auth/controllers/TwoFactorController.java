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

import dev.gitnode.os.auth.services.TwoFactorService;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/2fa")
@RequiredArgsConstructor
@NullMarked
class TwoFactorController {

  record CodeForm(@NotBlank @Size(max = 32) String code) {}

  record DisableForm(
      @NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 32) String code) {}

  record RecoveryCodes(List<String> recoveryCodes) {}

  private final TwoFactorService twoFactor;
  private final JwtUtils jwt;

  @GetMapping
  public TwoFactorService.Status status(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String auth) {
    return this.twoFactor.status(this.jwt.extractUserId(auth));
  }

  @PostMapping("/setup")
  @RateLimit(limit = 10, windowSeconds = 300, key = "2fa.setup")
  public TwoFactorService.Setup setup(final @RequestHeader(HttpHeaders.AUTHORIZATION) String auth) {
    return this.twoFactor.setup(this.jwt.extractUserId(auth));
  }

  @PostMapping("/enable")
  @RateLimit(limit = 10, windowSeconds = 300, key = "2fa.enable")
  public RecoveryCodes enable(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String auth,
      final @RequestBody @Valid CodeForm form) {
    return new RecoveryCodes(this.twoFactor.enable(this.jwt.extractUserId(auth), form.code()));
  }

  @PostMapping("/disable")
  @RateLimit(limit = 10, windowSeconds = 300, key = "2fa.disable")
  public ResponseEntity<Void> disable(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String auth,
      final @RequestBody @Valid DisableForm form) {
    this.twoFactor.disable(this.jwt.extractUserId(auth), form.password(), form.code());
    return ResponseEntity.noContent().build();
  }
}
