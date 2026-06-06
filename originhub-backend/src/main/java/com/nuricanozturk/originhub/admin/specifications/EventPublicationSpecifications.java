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
package com.nuricanozturk.originhub.admin.specifications;

import com.nuricanozturk.originhub.admin.dtos.ModulithEventLifecycleFilter;
import com.nuricanozturk.originhub.admin.entities.EventPublicationRecord;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

@NullMarked
public final class EventPublicationSpecifications {

  private EventPublicationSpecifications() {}

  public static Specification<EventPublicationRecord> search(
      final @Nullable String q,
      final @Nullable String eventType,
      final @Nullable String listenerId,
      final @Nullable String status,
      final ModulithEventLifecycleFilter lifecycle,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    return (root, query, cb) -> {
      final List<Predicate> predicates = new ArrayList<>();
      addFieldPredicates(predicates, cb, root, eventType, listenerId, status, from, to);
      addFullTextPredicate(predicates, cb, root, q);
      applyLifecycleFilter(lifecycle, root, cb, predicates);
      return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static void addFieldPredicates(
      final List<Predicate> predicates,
      final CriteriaBuilder cb,
      final Root<EventPublicationRecord> root,
      final @Nullable String eventType,
      final @Nullable String listenerId,
      final @Nullable String status,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    if (eventType != null) {
      predicates.add(cb.equal(root.get("eventType"), eventType));
    }
    if (listenerId != null) {
      predicates.add(containsIgnoreCase(cb, root, "listenerId", listenerId));
    }
    if (status != null) {
      predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase(Locale.ROOT)));
    }
    if (from != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("publicationDate"), from));
    }
    if (to != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("publicationDate"), to));
    }
  }

  private static void addFullTextPredicate(
      final List<Predicate> predicates,
      final CriteriaBuilder cb,
      final Root<EventPublicationRecord> root,
      final @Nullable String q) {

    if (q == null) {
      return;
    }
    predicates.add(
        cb.or(
            containsIgnoreCase(cb, root, "listenerId", q),
            containsIgnoreCase(cb, root, "eventType", q),
            containsIgnoreCaseOrEmpty(cb, root, "status", q),
            containsIgnoreCase(cb, root, "serializedEvent", q)));
  }

  private static void applyLifecycleFilter(
      final ModulithEventLifecycleFilter lifecycle,
      final Root<EventPublicationRecord> root,
      final CriteriaBuilder cb,
      final List<Predicate> predicates) {

    switch (lifecycle) {
      case ALL -> {
        // no-op
      }
      case COMPLETED ->
          predicates.add(
              cb.or(
                  cb.equal(cb.upper(root.get("status")), "COMPLETED"),
                  cb.isNotNull(root.get("completionDate"))));
      case IN_PROGRESS -> predicates.add(cb.equal(cb.upper(root.get("status")), "PROCESSING"));
      case INCOMPLETE -> predicates.add(cb.isNull(root.get("completionDate")));
      case FAILED -> predicates.add(cb.equal(cb.upper(root.get("status")), "FAILED"));
    }
  }

  private static Predicate containsIgnoreCase(
      final CriteriaBuilder cb,
      final Root<EventPublicationRecord> root,
      final String field,
      final String term) {

    return cb.like(cb.lower(root.get(field)), containsPattern(term));
  }

  private static Predicate containsIgnoreCaseOrEmpty(
      final CriteriaBuilder cb,
      final Root<EventPublicationRecord> root,
      final String field,
      final String term) {

    return cb.like(cb.lower(cb.coalesce(root.get(field), "")), containsPattern(term));
  }

  private static String containsPattern(final String term) {

    return "%" + term.toLowerCase(Locale.ROOT) + "%";
  }
}
