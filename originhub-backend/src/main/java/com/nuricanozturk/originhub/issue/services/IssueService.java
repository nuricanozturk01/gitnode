package com.nuricanozturk.originhub.issue.services;

import com.nuricanozturk.originhub.issue.api.IssueData;
import com.nuricanozturk.originhub.issue.api.IssueQueryService;
import com.nuricanozturk.originhub.issue.api.TaskQueryPort;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentForm;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentUpdateForm;
import com.nuricanozturk.originhub.issue.dtos.IssueDetail;
import com.nuricanozturk.originhub.issue.dtos.IssueForm;
import com.nuricanozturk.originhub.issue.dtos.IssueInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueLinkedTaskInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueUpdateForm;
import com.nuricanozturk.originhub.issue.entities.Issue;
import com.nuricanozturk.originhub.issue.entities.IssueComment;
import com.nuricanozturk.originhub.issue.entities.IssueStatus;
import com.nuricanozturk.originhub.issue.mappers.IssueMapper;
import com.nuricanozturk.originhub.issue.repositories.IssueCommentRepository;
import com.nuricanozturk.originhub.issue.repositories.IssueRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.issue.events.IssueDeletedEvent;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class IssueService implements IssueQueryService {

  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final String ERR_ISSUE_NOT_FOUND = "Issue #%d not found.";
  private static final String ERR_REPO_NOT_FOUND = "Repo not found.";

  private final @NonNull IssueRepository issueRepository;
  private final @NonNull IssueCommentRepository commentRepository;
  private final @NonNull RepoRepository repoRepository;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull IssueMapper issueMapper;
  private final @NonNull TaskQueryPort taskQueryPort;
  private final @NonNull ApplicationEventPublisher eventPublisher;

  @Transactional
  public @NonNull IssueDetail create(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull UUID authorId,
      final @NonNull IssueForm form) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var author =
        this.tenantRepository
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    final var nextNumber = this.issueRepository.findMaxNumberByRepoId(repo.getId()) + 1;

    final var issue = new Issue();
    issue.setRepo(repo);
    issue.setNumber(nextNumber);
    issue.setTitle(form.getTitle());
    issue.setDescription(form.getDescription());
    issue.setStatus(IssueStatus.OPEN.name());
    issue.setAuthor(author);

    if (form.getAssigneeId() != null) {
      final var assignee =
          this.tenantRepository
              .findById(form.getAssigneeId())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      issue.setAssignee(assignee);
    }

    final var saved = this.issueRepository.save(issue);
    return this.issueMapper.toDetail(saved, 0);
  }

  public @NonNull PageResponse<IssueInfo> getAll(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull String status,
      final int page) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var validStatus = this.validateStatus(status);
    final var pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("createdAt").descending());

    final var issuePage =
        this.issueRepository.findAllByRepoIdAndStatusOrderByCreatedAtDesc(
            repo.getId(), validStatus, pageable);

    return PageResponse.from(
        issuePage.map(
            issue -> {
              final int commentCount = this.commentRepository.countByIssueId(issue.getId());
              return this.issueMapper.toInfo(issue, commentCount);
            }));
  }

  public @NonNull IssueDetail get(
      final @NonNull String owner, final @NonNull String repoName, final int number) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final int commentCount = this.commentRepository.countByIssueId(issue.getId());
    return this.issueMapper.toDetail(issue, commentCount);
  }

  public @NonNull PageResponse<IssueCommentInfo> getComments(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final int page) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final var pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("createdAt").ascending());
    return PageResponse.from(
        this.commentRepository
            .findAllByIssueIdOrderByCreatedAtAsc(issue.getId(), pageable)
            .map(this.issueMapper::toCommentInfo));
  }

  @Transactional
  public @NonNull IssueDetail update(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final @NonNull IssueUpdateForm form,
      final @NonNull UUID requesterId) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    this.assertCanModify(requesterId, issue.getAuthor(), owner);
    this.setIssueStatuses(form, issue);

    if (form.getAssigneeId() != null) {
      final var assignee =
          this.tenantRepository
              .findById(form.getAssigneeId())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      issue.setAssignee(assignee);
    }

    final var saved = this.issueRepository.save(issue);
    final int commentCount = this.commentRepository.countByIssueId(saved.getId());
    return this.issueMapper.toDetail(saved, commentCount);
  }

  private void setIssueStatuses(final @NonNull IssueUpdateForm form, final Issue issue) {

    if (form.getTitle() != null) {
      issue.setTitle(form.getTitle());
    }
    if (form.getDescription() != null) {
      issue.setDescription(form.getDescription());
    }

    if (form.getStatus() != null) {
      final var newStatus = this.validateStatus(form.getStatus());
      issue.setStatus(newStatus);

      if (IssueStatus.CLOSED.name().equals(newStatus) && issue.getClosedAt() == null) {
        issue.setClosedAt(Instant.now());
      } else if (IssueStatus.OPEN.name().equals(newStatus)) {
        issue.setClosedAt(null);
      }
    }
  }

  @Transactional
  public void delete(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final @NonNull UUID requesterId) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    this.assertCanModify(requesterId, issue.getAuthor(), owner);
    final var issueId = issue.getId();
    this.issueRepository.delete(issue);
    this.eventPublisher.publishEvent(new IssueDeletedEvent(issueId));
  }

  @Transactional
  public @NonNull IssueCommentInfo addComment(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final @NonNull UUID authorId,
      final @NonNull IssueCommentForm form) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final var author =
        this.tenantRepository
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    final var comment = new IssueComment();
    comment.setIssue(issue);
    comment.setAuthor(author);
    comment.setBody(form.getBody());

    return this.issueMapper.toCommentInfo(this.commentRepository.save(comment));
  }

  @Transactional
  public @NonNull IssueCommentInfo updateComment(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final @NonNull UUID commentId,
      final @NonNull IssueCommentUpdateForm form,
      final @NonNull UUID requesterId) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final var comment =
        this.commentRepository
            .findByIdAndIssueId(commentId, issue.getId())
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));

    this.assertCanModify(requesterId, comment.getAuthor(), owner);
    comment.setBody(form.getBody());
    return this.issueMapper.toCommentInfo(this.commentRepository.save(comment));
  }

  @Transactional
  public void deleteComment(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final @NonNull UUID commentId,
      final @NonNull UUID requesterId) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final var comment =
        this.commentRepository
            .findByIdAndIssueId(commentId, issue.getId())
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));

    this.assertCanModify(requesterId, comment.getAuthor(), owner);
    this.commentRepository.delete(comment);
  }

  public @NonNull List<IssueLinkedTaskInfo> getLinkedTasks(
      final @NonNull String owner, final @NonNull String repoName, final int number) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException("Issue #" + number + " not found"));

    return this.taskQueryPort.findByLinkedIssueId(issue.getId()).stream()
        .map(
            t ->
                IssueLinkedTaskInfo.builder()
                    .taskCode(t.code())
                    .taskTitle(t.title())
                    .taskStatus(t.status())
                    .projectCode(t.projectCode())
                    .projectName(t.projectName())
                    .build())
        .toList();
  }

  @Override
  public Optional<IssueData> findById(final @NonNull UUID id) {
    return this.issueRepository
        .findById(id)
        .map(i -> new IssueData(i.getId(), i.getNumber(), i.getTitle(), i.getStatus()));
  }

  private void assertCanModify(
      final @NonNull UUID requesterId,
      final @NonNull Tenant author,
      final @NonNull String repoOwnerUsername) {

    if (requesterId.equals(author.getId())) {
      return;
    }

    final var repoOwner = this.tenantRepository.findByUsername(repoOwnerUsername);
    final boolean isRepoOwner =
        repoOwner.isPresent() && requesterId.equals(repoOwner.get().getId());

    if (!isRepoOwner && !this.isAdmin(requesterId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }

  private boolean isAdmin(final @NonNull UUID tenantId) {
    return this.tenantRepository.findById(tenantId).map(Tenant::isAdmin).orElse(false);
  }

  private @NonNull String validateStatus(final @NonNull String status) {
    final var valid = Arrays.stream(IssueStatus.values()).map(Enum::name).toList();
    if (!valid.contains(status)) {
      throw new ErrorOccurredException("Invalid status: " + status + ". Valid: " + valid);
    }
    return status;
  }
}
