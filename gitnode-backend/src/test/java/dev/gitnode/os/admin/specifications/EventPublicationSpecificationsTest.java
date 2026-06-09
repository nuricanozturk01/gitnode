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
package dev.gitnode.os.admin.specifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import dev.gitnode.os.admin.dtos.ModulithEventLifecycleFilter;
import dev.gitnode.os.admin.entities.EventPublicationRecord;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublicationSpecifications unit tests")
class EventPublicationSpecificationsTest {

  @Mock private CriteriaBuilder cb;
  @Mock private CriteriaQuery<Object> query;
  @Mock private Root<EventPublicationRecord> root;
  @Mock private Predicate predicate;
  @Mock private Path<Object> path;
  @Mock private Expression<String> stringExpression;

  @BeforeEach
  void setUp() {
    lenient().when(root.get(anyString())).thenReturn(path);
    lenient().when(cb.conjunction()).thenReturn(predicate);
    lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);
    lenient().when(cb.or(any(), any())).thenReturn(predicate);
    lenient().when(cb.or(any(), any(), any(), any())).thenReturn(predicate);
    lenient().when(cb.equal(any(), any())).thenReturn(predicate);
    lenient().when(cb.isNotNull(any())).thenReturn(predicate);
    lenient().when(cb.isNull(any())).thenReturn(predicate);
    lenient().when(cb.like(any(), anyString())).thenReturn(predicate);
    lenient().when(cb.lower(any())).thenReturn(stringExpression);
    lenient().when(cb.upper(any())).thenReturn(stringExpression);
    lenient().when(cb.coalesce(any(), anyString())).thenReturn(stringExpression);
    lenient()
        .when(cb.greaterThanOrEqualTo(any(Expression.class), any(Instant.class)))
        .thenReturn(predicate);
    lenient()
        .when(cb.lessThanOrEqualTo(any(Expression.class), any(Instant.class)))
        .thenReturn(predicate);
  }

  @Test
  @DisplayName("returns conjunction when no filters are provided")
  void search_returnsConjunction_whenNoFilters() {
    var spec =
        EventPublicationSpecifications.search(
            null, null, null, null, ModulithEventLifecycleFilter.ALL, null, null);

    var result = spec.toPredicate(root, query, cb);

    assertThat(result).isSameAs(predicate);
    verify(cb).conjunction();
  }

  @Test
  @DisplayName("adds field predicates and lifecycle filter")
  void search_addsFieldPredicates() {
    var from = Instant.parse("2026-01-01T00:00:00Z");
    var to = Instant.parse("2026-01-31T23:59:59Z");
    var spec =
        EventPublicationSpecifications.search(
            null,
            "IssueCreatedEvent",
            "issue-listener",
            "failed",
            ModulithEventLifecycleFilter.FAILED,
            from,
            to);

    var result = spec.toPredicate(root, query, cb);

    assertThat(result).isSameAs(predicate);
    verify(cb).and(any(Predicate[].class));
    verify(cb).equal(root.get("eventType"), "IssueCreatedEvent");
    verify(cb, atLeastOnce()).equal(stringExpression, "FAILED");
    verify(cb).greaterThanOrEqualTo(root.get("publicationDate"), from);
    verify(cb).lessThanOrEqualTo(root.get("publicationDate"), to);
  }

  @Test
  @DisplayName("adds full-text OR predicate when q is provided")
  void search_addsFullTextPredicate_whenQueryProvided() {
    var spec =
        EventPublicationSpecifications.search(
            "repo", null, null, null, ModulithEventLifecycleFilter.ALL, null, null);

    spec.toPredicate(root, query, cb);

    verify(cb).or(any(), any(), any(), any());
  }

  @Test
  @DisplayName("completed lifecycle adds completion predicate")
  void search_addsCompletedPredicate() {
    var spec =
        EventPublicationSpecifications.search(
            null, null, null, null, ModulithEventLifecycleFilter.COMPLETED, null, null);

    spec.toPredicate(root, query, cb);

    verify(cb).or(any(), any());
    verify(cb).equal(stringExpression, "COMPLETED");
    verify(cb).isNotNull(root.get("completionDate"));
  }

  @Test
  @DisplayName("incomplete lifecycle filters null completion date")
  void search_addsIncompletePredicate() {
    var spec =
        EventPublicationSpecifications.search(
            null, null, null, null, ModulithEventLifecycleFilter.INCOMPLETE, null, null);

    spec.toPredicate(root, query, cb);

    verify(cb).isNull(root.get("completionDate"));
  }

  @Test
  @DisplayName("in-progress lifecycle filters processing status")
  void search_addsInProgressPredicate() {
    var spec =
        EventPublicationSpecifications.search(
            null, null, null, null, ModulithEventLifecycleFilter.IN_PROGRESS, null, null);

    spec.toPredicate(root, query, cb);

    verify(cb).equal(stringExpression, "PROCESSING");
  }
}
