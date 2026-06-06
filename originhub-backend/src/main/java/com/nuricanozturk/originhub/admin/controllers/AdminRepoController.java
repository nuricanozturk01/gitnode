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

import com.nuricanozturk.originhub.admin.dtos.AdminRepoSummary;
import com.nuricanozturk.originhub.admin.services.AdminRepoService;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/repos")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminRepoController {

  private static final int MAX_PAGE_SIZE = 100;

  private final AdminRepoService adminRepoService;

  @GetMapping
  public ResponseEntity<PageResponse<AdminRepoSummary>> list(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final String q,
      @RequestParam(required = false) final String owner) {

    final var pageable =
        PageRequest.of(
            page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(
        PageResponse.from(this.adminRepoService.listRepos(pageable, q, owner)));
  }
}
