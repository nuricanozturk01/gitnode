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
package com.nuricanozturk.originhub.shared.audit.specifications;

import com.nuricanozturk.originhub.shared.audit.entities.AuditLog;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

@NullMarked
public final class AuditLogSpecifications {

  private AuditLogSpecifications() {}

  public static Specification<AuditLog> search(
      final @Nullable String q,
      final @Nullable String actor,
      final @Nullable String action,
      final @Nullable String entityType,
      final @Nullable String entityId,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    return (root, query, cb) -> {
      final List<Predicate> predicates = new ArrayList<>();
      addFieldPredicates(predicates, cb, root, actor, action, entityType, entityId, from, to);
      addFullTextPredicate(predicates, cb, root, q);
      return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static void addFieldPredicates(
      final List<Predicate> predicates,
      final CriteriaBuilder cb,
      final Root<AuditLog> root,
      final @Nullable String actor,
      final @Nullable String action,
      final @Nullable String entityType,
      final @Nullable String entityId,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    if (actor != null) {
      predicates.add(containsIgnoreCase(cb, root, "actorUsername", actor));
    }
    if (action != null) {
      predicates.add(cb.equal(root.get("action"), action));
    }
    if (entityType != null) {
      predicates.add(cb.equal(root.get("entityType"), entityType));
    }
    if (entityId != null) {
      predicates.add(containsIgnoreCase(cb, root, "entityId", entityId));
    }
    if (from != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
    }
    if (to != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
    }
  }

  private static void addFullTextPredicate(
      final List<Predicate> predicates,
      final CriteriaBuilder cb,
      final Root<AuditLog> root,
      final @Nullable String q) {

    if (q == null) {
      return;
    }
    predicates.add(
        cb.or(
            containsIgnoreCase(cb, root, "actorUsername", q),
            containsIgnoreCase(cb, root, "action", q),
            containsIgnoreCaseOrEmpty(cb, root, "entityType", q),
            containsIgnoreCaseOrEmpty(cb, root, "entityId", q),
            containsIgnoreCaseOrEmpty(cb, root, "details", q),
            containsIgnoreCaseOrEmpty(cb, root, "ipAddress", q)));
  }

  private static Predicate containsIgnoreCase(
      final CriteriaBuilder cb, final Root<AuditLog> root, final String field, final String term) {

    return cb.like(cb.lower(root.get(field)), containsPattern(term));
  }

  private static Predicate containsIgnoreCaseOrEmpty(
      final CriteriaBuilder cb, final Root<AuditLog> root, final String field, final String term) {

    return cb.like(cb.lower(cb.coalesce(root.get(field), "")), containsPattern(term));
  }

  private static String containsPattern(final String term) {

    return "%" + term.toLowerCase() + "%";
  }
}
