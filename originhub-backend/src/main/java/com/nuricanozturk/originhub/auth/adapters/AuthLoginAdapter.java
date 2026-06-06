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
package com.nuricanozturk.originhub.auth.adapters;

import com.nuricanozturk.originhub.auth.api.AuthLoginPort;
import com.nuricanozturk.originhub.auth.dtos.LoginForm;
import com.nuricanozturk.originhub.auth.services.AuthService;
import com.nuricanozturk.originhub.shared.auth.dtos.LoginInfo;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class AuthLoginAdapter implements AuthLoginPort {

  private final AuthService authService;

  @Override
  public LoginInfo login(final String usernameOrEmail, final String password) {

    final var form =
        LoginForm.builder().usernameOrEmail(usernameOrEmail).password(password).build();

    return this.authService.login(form);
  }
}
