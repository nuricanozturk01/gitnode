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
package com.nuricanozturk.originhub.auth.repositories;

import com.nuricanozturk.originhub.auth.entities.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

  Optional<Organization> findBySlug(String slug);

  Page<Organization> findAllByOrderByCreatedAtDesc(Pageable pageable);

  boolean existsBySlug(String slug);

  @Query(
      value =
          """
          SELECT o.* FROM organization o
          WHERE o.sso_enabled = TRUE
            AND EXISTS (
              SELECT 1 FROM unnest(o.email_domains) AS d
              WHERE LOWER(d) = LOWER(:domain)
            )
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<Organization> findSsoEnabledByEmailDomain(@Param("domain") String domain);

  @Query(
      value =
          """
          SELECT o.* FROM organization o
          WHERE o.ldap_enabled = TRUE
            AND EXISTS (
              SELECT 1 FROM unnest(o.email_domains) AS d
              WHERE LOWER(d) = LOWER(:domain)
            )
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<Organization> findLdapEnabledByEmailDomain(@Param("domain") String domain);

  long countBySsoEnabledTrue();

  long countByLdapEnabledTrue();
}
