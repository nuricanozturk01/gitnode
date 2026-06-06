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
package com.nuricanozturk.originhub.admin.repositories;

import com.nuricanozturk.originhub.admin.entities.EventPublicationRecord;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface EventPublicationRepository
    extends JpaRepository<EventPublicationRecord, UUID>,
        JpaSpecificationExecutor<EventPublicationRecord> {

  @Query("SELECT DISTINCT e.eventType FROM EventPublicationRecord e ORDER BY e.eventType")
  Page<String> findDistinctEventTypes(Pageable pageable);

  @Query("SELECT DISTINCT e.listenerId FROM EventPublicationRecord e ORDER BY e.listenerId")
  Page<String> findDistinctListenerIds(Pageable pageable);

  @Query(
      "SELECT DISTINCT e.status FROM EventPublicationRecord e WHERE e.status IS NOT NULL ORDER BY e.status")
  Page<String> findDistinctStatuses(Pageable pageable);
}
