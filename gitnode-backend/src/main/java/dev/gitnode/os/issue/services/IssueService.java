package dev.gitnode.os.issue.services;

import dev.gitnode.os.events.issue.IssueCommentedEvent;
import dev.gitnode.os.events.issue.IssueCreatedEvent;
import dev.gitnode.os.events.issue.IssueDeletedEvent;
import dev.gitnode.os.events.issue.IssueStatusChangedEvent;
import dev.gitnode.os.events.issue.IssueUpdatedEvent;
import dev.gitnode.os.issue.api.IssueData;
import dev.gitnode.os.issue.api.IssueQueryService;
import dev.gitnode.os.issue.api.TaskQueryPort;
import dev.gitnode.os.issue.dtos.IssueCommentForm;
import dev.gitnode.os.issue.dtos.IssueCommentInfo;
import dev.gitnode.os.issue.dtos.IssueCommentUpdateForm;
import dev.gitnode.os.issue.dtos.IssueDetail;
import dev.gitnode.os.issue.dtos.IssueForm;
import dev.gitnode.os.issue.dtos.IssueInfo;
import dev.gitnode.os.issue.dtos.IssueLinkedTaskInfo;
import dev.gitnode.os.issue.dtos.IssueUpdateForm;
import dev.gitnode.os.issue.entities.Issue;
import dev.gitnode.os.issue.entities.IssueComment;
import dev.gitnode.os.issue.entities.IssueStatus;
import dev.gitnode.os.issue.mappers.IssueMapper;
import dev.gitnode.os.issue.repositories.IssueCommentRepository;
import dev.gitnode.os.issue.repositories.IssueRepository;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class IssueService implements IssueQueryService {

  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final String ERR_ISSUE_NOT_FOUND = "Issue #%d not found.";
  private static final String ERR_REPO_NOT_FOUND = "Repo not found.";

  private final IssueRepository issueRepository;
  private final IssueCommentRepository commentRepository;
  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final IssueMapper issueMapper;
  private final TaskQueryPort taskQueryPort;
  private final ApplicationEventPublisher eventPublisher;
  private final CollaboratorAccessPort collaboratorAccessPort;

  @Cacheable(cacheNames = CacheNames.REPO_ISSUE_OPEN_COUNT, key = "#owner + ':' + #repoName")
  public int countOpenIssues(final String owner, final String repoName) {
    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    return this.issueRepository.countByRepoIdAndStatus(repo.getId(), IssueStatus.OPEN.name());
  }

  @CacheEvict(cacheNames = CacheNames.REPO_ISSUE_OPEN_COUNT, key = "#owner + ':' + #repoName")
  @Audited(
      action = "CREATE_ISSUE",
      entityType = "ISSUE",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', title=' + #form.title")
  @Transactional
  public IssueDetail create(
      final String owner, final String repoName, final UUID authorId, final IssueForm form) {

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
    this.eventPublisher.publishEvent(
        new IssueCreatedEvent(saved.getId(), repo.getId(), saved.getNumber(), saved.getTitle()));
    return this.issueMapper.toDetail(saved, 0);
  }

  public PageResponse<IssueInfo> getAll(
      final String owner, final String repoName, final String status, final int page) {

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

  public IssueDetail get(final String owner, final String repoName, final int number) {

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

  public PageResponse<IssueCommentInfo> getComments(
      final String owner, final String repoName, final int number, final int page) {

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

  @CacheEvict(
      cacheNames = CacheNames.REPO_ISSUE_OPEN_COUNT,
      key = "#owner + ':' + #repoName",
      condition = "#form.status != null")
  @Transactional
  public IssueDetail update(
      final String owner,
      final String repoName,
      final int number,
      final IssueUpdateForm form,
      final UUID requesterId) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    this.assertCanModify(requesterId, issue.getAuthor(), owner, repo.getId());
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
    if (form.getStatus() != null) {
      this.eventPublisher.publishEvent(
          new IssueStatusChangedEvent(
              saved.getId(), repo.getId(), saved.getNumber(), saved.getStatus()));
    } else {
      this.eventPublisher.publishEvent(
          new IssueUpdatedEvent(saved.getId(), repo.getId(), saved.getNumber()));
    }
    return this.issueMapper.toDetail(saved, commentCount);
  }

  private void setIssueStatuses(final IssueUpdateForm form, final Issue issue) {

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

  @CacheEvict(cacheNames = CacheNames.REPO_ISSUE_OPEN_COUNT, key = "#owner + ':' + #repoName")
  @Audited(
      action = "DELETE_ISSUE",
      entityType = "ISSUE",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public void delete(
      final String owner, final String repoName, final int number, final UUID requesterId) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    this.assertCanModify(requesterId, issue.getAuthor(), owner, repo.getId());
    final var issueId = issue.getId();
    this.issueRepository.delete(issue);
    this.eventPublisher.publishEvent(new IssueDeletedEvent(issueId));
  }

  @Transactional
  public IssueCommentInfo addComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID authorId,
      final IssueCommentForm form) {

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

    final var saved = this.commentRepository.save(comment);

    final Set<UUID> participants =
        new HashSet<>(this.commentRepository.findDistinctCommenterIdsByIssueId(issue.getId()));
    participants.add(issue.getAuthor().getId());
    participants.remove(authorId);

    this.eventPublisher.publishEvent(
        new IssueCommentedEvent(
            saved.getId(),
            issue.getId(),
            repo.getId(),
            issue.getNumber(),
            saved.getBody(),
            authorId,
            issue.getAuthor().getId(),
            owner,
            repoName,
            Set.copyOf(participants)));
    return this.issueMapper.toCommentInfo(saved);
  }

  @Transactional
  public IssueCommentInfo updateComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final IssueCommentUpdateForm form,
      final UUID requesterId) {
    final var commentRepo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    final var comment = this.findCommentOrThrow(owner, repoName, number, commentId);
    this.assertCanModify(requesterId, comment.getAuthor(), owner, commentRepo.getId());
    comment.setBody(form.getBody());
    return this.issueMapper.toCommentInfo(this.commentRepository.save(comment));
  }

  @Transactional
  public void deleteComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final UUID requesterId) {
    final var deleteRepo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    final var comment = this.findCommentOrThrow(owner, repoName, number, commentId);
    this.assertCanModify(requesterId, comment.getAuthor(), owner, deleteRepo.getId());
    this.commentRepository.delete(comment);
  }

  private IssueComment findCommentOrThrow(
      final String owner, final String repoName, final int number, final UUID commentId) {
    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.getId(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));
    return this.commentRepository
        .findByIdAndIssueId(commentId, issue.getId())
        .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
  }

  public List<IssueLinkedTaskInfo> getLinkedTasks(
      final String owner, final String repoName, final int number) {

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
  public Optional<IssueData> findById(final UUID id) {
    return this.issueRepository
        .findById(id)
        .map(i -> new IssueData(i.getId(), i.getNumber(), i.getTitle(), i.getStatus()));
  }

  private void assertCanModify(
      final UUID requesterId,
      final Tenant author,
      final String repoOwnerUsername,
      final UUID repoId) {

    if (requesterId.equals(author.getId())) {
      return;
    }

    final var repoOwner = this.tenantRepository.findByUsername(repoOwnerUsername);
    final boolean isRepoOwner =
        repoOwner.isPresent() && requesterId.equals(repoOwner.get().getId());

    if (isRepoOwner) {
      return;
    }

    if (this.collaboratorAccessPort.hasPermission(
        repoId, requesterId, CollaboratorPermission.ISSUE_MANAGE)) {
      return;
    }

    throw new AccessNotAllowedException("notAuthorized");
  }

  private String validateStatus(final String status) {
    final var valid = Arrays.stream(IssueStatus.values()).map(Enum::name).toList();
    if (!valid.contains(status)) {
      throw new ErrorOccurredException("Invalid status: " + status + ". Valid: " + valid);
    }
    return status;
  }
}
