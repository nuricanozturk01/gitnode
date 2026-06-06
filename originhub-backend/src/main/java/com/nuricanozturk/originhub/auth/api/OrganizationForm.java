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
package com.nuricanozturk.originhub.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record OrganizationForm(
    @NotBlank @Size(max = 255) String name,
    @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$", message = "invalidSlug")
        String slug,
    @NotEmpty List<@NotBlank @Size(max = 255) String> emailDomains) {}
