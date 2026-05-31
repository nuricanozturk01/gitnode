package com.nuricanozturk.originhub.issue.entities;

import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "issues")
public class Issue {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "repo_id", nullable = false)
  private Repo repo;

  @Column(name = "number", nullable = false)
  private int number;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "description", length = Integer.MAX_VALUE)
  private String description;

  @ColumnDefault("'OPEN'")
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "author_id", nullable = false)
  private Tenant author;

  @ManyToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.SET_NULL)
  @JoinColumn(name = "assignee_id")
  private Tenant assignee;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;
}
