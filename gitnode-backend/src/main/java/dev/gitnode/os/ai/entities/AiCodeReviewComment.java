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
package dev.gitnode.os.ai.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "ai_code_review_comments")
public class AiCodeReviewComment {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "review_id", nullable = false)
  private AiCodeReview review;

  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @Column(name = "line_number")
  private Integer lineNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private ReviewCategory category = ReviewCategory.GENERAL;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 20)
  private ReviewSeverity severity = ReviewSeverity.INFO;

  @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
  private String comment;

  @Column(name = "suggestion", columnDefinition = "TEXT")
  private String suggestion;
}
