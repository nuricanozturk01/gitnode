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
package com.nuricanozturk.originhub.actions.repositories;

import com.nuricanozturk.originhub.actions.entities.RunnerGroup;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface RunnerGroupRepository extends JpaRepository<RunnerGroup, Long> {

  Page<RunnerGroup> findAllByOrgId(Long orgId, Pageable pageable);

  Optional<RunnerGroup> findByOrgIdAndName(Long orgId, String name);

  boolean existsByOrgIdAndName(Long orgId, String name);
}
