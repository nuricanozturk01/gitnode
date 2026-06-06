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
package com.nuricanozturk.originhub.admin.controllers;

import com.nuricanozturk.originhub.admin.dtos.AdminLoginForm;
import com.nuricanozturk.originhub.admin.dtos.AdminLoginInfo;
import com.nuricanozturk.originhub.admin.services.AdminAuthService;
import com.nuricanozturk.originhub.shared.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@NullMarked
public class AdminAuthController {

  private final AdminAuthService adminAuthService;

  @PostMapping("/login")
  @RateLimit(limit = 10, windowSeconds = 60, key = "admin.auth.login")
  public ResponseEntity<AdminLoginInfo> login(@RequestBody @Valid final AdminLoginForm form) {

    return ResponseEntity.ok(this.adminAuthService.login(form));
  }
}
