package dev.gitnode.os.issue.services;

import dev.gitnode.os.events.issue.IssueCommentedEvent;
import dev.gitnode.os.events.issue.IssueCreatedEvent;
import dev.gitnode.os.events.issue.IssueDeletedEvent;
import dev.gitnode.os.events.issue.IssueStatusChangedEvent;
import dev.gitnode.os.events.issue.IssueUpdatedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
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
import dev.gitnode.os.issue.repositories.IssueLabelRepository;
import dev.gitnode.os.issue.repositories.IssueRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
  private final RepoLookupPort repoLookup;
  private final RepoCommandPort repoCommands;
  private final TenantQueryPort tenants;
  private final IssueMapper issueMapper;
  private final TaskQueryPort taskQueryPort;
  private final ApplicationEventPublisher eventPublisher;
  private final CollaboratorAccessPort collaboratorAccessPort;
  private final IssueLabelRepository labelRepository;
  private final dev.gitnode.os.issue.repositories.IssueMilestoneRepository milestoneRepository;

  @Audited(
      action = "CREATE_ISSUE",
      entityType = "ISSUE",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', title=' + #form.title")
  @Transactional
  public IssueDetail create(
      final String owner, final String repoName, final UUID authorId, final IssueForm form) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var author =
        this.tenants
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    if (!this.repoCommands.lockForUpdate(repo.id())) {
      throw new ItemNotFoundException(ERR_REPO_NOT_FOUND);
    }
    final var nextNumber = this.issueRepository.findMaxNumberByRepoId(repo.id()) + 1;

    final var issue = new Issue();
    issue.setRepoId(repo.id());
    issue.setNumber(nextNumber);
    issue.setTitle(form.getTitle());
    issue.setDescription(form.getDescription());
    issue.setStatus(IssueStatus.OPEN.name());
    issue.setAuthorId(author.id());

    if (form.getAssigneeId() != null) {
      final var assignee =
          this.tenants
              .findById(form.getAssigneeId())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      issue.setAssigneeId(assignee.id());
      issue.getAssigneeIds().add(assignee.id());
    }

    final var saved = this.issueRepository.save(issue);
    this.eventPublisher.publishEvent(
        new IssueCreatedEvent(saved.getId(), repo.id(), saved.getNumber(), saved.getTitle()));
    return this.issueMapper.toDetail(saved, 0);
  }

  public PageResponse<IssueInfo> getAll(
      final String owner, final String repoName, final String status, final int page) {

    return this.getAll(owner, repoName, status, page, null);
  }

  public PageResponse<IssueInfo> getAll(
      final String owner,
      final String repoName,
      final String status,
      final int page,
      final @Nullable UUID labelId) {

    return this.getAll(owner, repoName, status, page, labelId, null);
  }

  public PageResponse<IssueInfo> getAll(
      final String owner,
      final String repoName,
      final String status,
      final int page,
      final @Nullable UUID labelId,
      final @Nullable UUID milestoneId) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var validStatus = this.validateStatus(status);
    final var pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("createdAt").descending());

    final var issuePage =
        milestoneId != null
            ? this.issueRepository.findAllByMilestone(
                repo.id(), validStatus, milestoneId, labelId, pageable)
            : labelId == null
                ? this.issueRepository.findAllByRepoIdAndStatusOrderByCreatedAtDesc(
                    repo.id(), validStatus, pageable)
                : this.issueRepository.findAllByLabel(repo.id(), validStatus, labelId, pageable);

    final var commentCounts = this.commentCountsOf(issuePage.getContent());

    return PageResponse.from(
        this.issueMapper.toInfoPage(
            issuePage, issue -> commentCounts.getOrDefault(issue.getId(), 0L).intValue()));
  }

  /** Comment counts of the given issues in one query. */
  private Map<UUID, Long> commentCountsOf(final List<Issue> issues) {
    final Map<UUID, Long> counts = new HashMap<>();
    if (!issues.isEmpty()) {
      this.commentRepository
          .countByIssueIds(issues.stream().map(Issue::getId).toList())
          .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
    }
    return counts;
  }

  public IssueDetail get(final String owner, final String repoName, final int number) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final int commentCount = this.commentRepository.countByIssueId(issue.getId());
    return this.issueMapper.toDetail(issue, commentCount);
  }

  public PageResponse<IssueCommentInfo> getComments(
      final String owner, final String repoName, final int number, final int page) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final var pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("createdAt").ascending());
    return PageResponse.from(
        this.issueMapper.toCommentInfoPage(
            this.commentRepository.findAllByIssueIdOrderByCreatedAtAsc(issue.getId(), pageable)));
  }

  @Transactional
  public IssueDetail update(
      final String owner,
      final String repoName,
      final int number,
      final IssueUpdateForm form,
      final UUID requesterId) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumberForUpdate(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    this.assertCanModify(requesterId, issue.getAuthorId(), repo);
    this.applyLabelChange(form, issue, repo.id());
    if (form.getMilestoneId() != null && form.isClearMilestone()) {
      throw new BadRequestException("Cannot set and clear a milestone in the same operation");
    }
    if (form.getMilestoneId() != null) {
      issue.setMilestone(
          this.milestoneRepository
              .findByIdAndRepoId(form.getMilestoneId(), repo.id())
              .orElseThrow(() -> new ItemNotFoundException("Milestone not found")));
    } else if (form.isClearMilestone()) {
      issue.setMilestone(null);
    }
    this.validateAssigneeChange(form);
    this.setIssueStatuses(form, issue);
    this.applyAssigneeChange(form, issue);

    final var saved = this.issueRepository.save(issue);
    final int commentCount = this.commentRepository.countByIssueId(saved.getId());
    if (form.getStatus() != null) {
      this.eventPublisher.publishEvent(
          new IssueStatusChangedEvent(
              saved.getId(), repo.id(), saved.getNumber(), saved.getStatus()));
    } else {
      this.eventPublisher.publishEvent(
          new IssueUpdatedEvent(saved.getId(), repo.id(), saved.getNumber()));
    }
    return this.issueMapper.toDetail(saved, commentCount);
  }

  private void applyLabelChange(final IssueUpdateForm form, final Issue issue, final UUID repoId) {
    if (form.getAddLabelId() != null && form.getRemoveLabelId() != null) {
      throw new BadRequestException("Cannot add and remove a label in the same operation");
    }
    if (form.getAddLabelId() != null) {
      final var label =
          this.labelRepository
              .findByIdAndRepoId(form.getAddLabelId(), repoId)
              .orElseThrow(() -> new ItemNotFoundException("Label not found"));
      if (issue.getLabels().stream()
          .noneMatch(existing -> existing.getId().equals(label.getId()))) {
        issue.getLabels().add(label);
      }
    } else if (form.getRemoveLabelId() != null) {
      issue.getLabels().removeIf(label -> label.getId().equals(form.getRemoveLabelId()));
    }
  }

  private void validateAssigneeChange(final IssueUpdateForm form) {
    final long assigneeOperations =
        java.util.stream.Stream.of(
                    form.getAssigneeId(),
                    form.getAssigneeUsername(),
                    form.getAddAssigneeUsername(),
                    form.getRemoveAssigneeUsername())
                .filter(java.util.Objects::nonNull)
                .count()
            + (form.isClearAssignee() ? 1 : 0);
    if (assigneeOperations > 1) {
      throw new BadRequestException("Conflicting assignee operations");
    }
    if (java.util.stream.Stream.of(
            form.getAssigneeUsername(),
            form.getAddAssigneeUsername(),
            form.getRemoveAssigneeUsername())
        .filter(java.util.Objects::nonNull)
        .anyMatch(String::isBlank)) {
      throw new BadRequestException("Assignee username must not be blank");
    }
  }

  private void applyAssigneeChange(final IssueUpdateForm form, final Issue issue) {
    if (form.isClearAssignee()) {
      issue.setAssigneeId(null);
      issue.getAssigneeIds().clear();
    } else if (form.getAssigneeId() != null) {
      final var assignee =
          this.tenants
              .findById(form.getAssigneeId())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      issue.setAssigneeId(assignee.id());
      issue.getAssigneeIds().clear();
      issue.getAssigneeIds().add(assignee.id());
    } else if (form.getAssigneeUsername() != null) {
      final var assignee =
          this.tenants
              .findByUsername(form.getAssigneeUsername().trim())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      issue.setAssigneeId(assignee.id());
      issue.getAssigneeIds().clear();
      issue.getAssigneeIds().add(assignee.id());
    } else if (form.getAddAssigneeUsername() != null) {
      this.addAssignee(issue, form.getAddAssigneeUsername());
    } else if (form.getRemoveAssigneeUsername() != null) {
      this.removeAssignee(issue, form.getRemoveAssigneeUsername());
    }
  }

  private void addAssignee(final Issue issue, final String username) {
    final var assignee =
        this.tenants
            .findByUsername(username.trim())
            .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
    issue.getAssigneeIds().add(assignee.id());
    if (issue.getAssigneeId() == null) issue.setAssigneeId(assignee.id());
  }

  private void removeAssignee(final Issue issue, final String rawUsername) {
    final var username = rawUsername.trim();
    final var known = new java.util.HashSet<>(issue.getAssigneeIds());
    if (issue.getAssigneeId() != null) {
      known.add(issue.getAssigneeId());
    }
    final var accounts = this.tenants.findAllByIds(known);
    issue
        .getAssigneeIds()
        .removeIf(
            id -> {
              final var account = accounts.get(id);
              return account != null && account.username().equals(username);
            });
    final var primary = issue.getAssigneeId() == null ? null : accounts.get(issue.getAssigneeId());
    if (primary != null && primary.username().equals(username)) {
      issue.setAssigneeId(
          issue.getAssigneeIds().stream()
              .map(accounts::get)
              .filter(java.util.Objects::nonNull)
              .min(java.util.Comparator.comparing(TenantQueryPort.TenantRef::username))
              .map(TenantQueryPort.TenantRef::id)
              .orElse(null));
    }
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

  @Audited(
      action = "DELETE_ISSUE",
      entityType = "ISSUE",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public void delete(
      final String owner, final String repoName, final int number, final UUID requesterId) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    this.assertCanModify(requesterId, issue.getAuthorId(), repo);
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
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));

    final var author =
        this.tenants
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    final var comment = new IssueComment();
    comment.setIssue(issue);
    comment.setAuthorId(author.id());
    comment.setBody(form.getBody());

    final var saved = this.commentRepository.save(comment);

    final Set<UUID> participants =
        new HashSet<>(this.commentRepository.findDistinctCommenterIdsByIssueId(issue.getId()));
    participants.add(issue.getAuthorId());
    participants.remove(authorId);

    this.eventPublisher.publishEvent(
        new IssueCommentedEvent(
            saved.getId(),
            issue.getId(),
            repo.id(),
            issue.getNumber(),
            saved.getBody(),
            authorId,
            issue.getAuthorId(),
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
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    final var comment = this.findCommentOrThrow(owner, repoName, number, commentId);
    this.assertCanModify(requesterId, comment.getAuthorId(), commentRepo);
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
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    final var comment = this.findCommentOrThrow(owner, repoName, number, commentId);
    this.assertCanModify(requesterId, comment.getAuthorId(), deleteRepo);
    this.commentRepository.delete(comment);
  }

  private IssueComment findCommentOrThrow(
      final String owner, final String repoName, final int number, final UUID commentId) {
    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException(ERR_ISSUE_NOT_FOUND.formatted(number)));
    return this.commentRepository
        .findByIdAndIssueId(commentId, issue.getId())
        .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
  }

  public List<IssueLinkedTaskInfo> getLinkedTasks(
      final String owner, final String repoName, final int number, final @Nullable UUID viewerId) {

    final var repo =
        this.repoLookup
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    final var issue =
        this.issueRepository
            .findByRepoIdAndNumber(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException("Issue #" + number + " not found"));

    return this.taskQueryPort.findByLinkedIssueId(issue.getId(), viewerId).stream()
        .map(
            t ->
                IssueLinkedTaskInfo.builder()
                    .taskCode(t.code())
                    .taskTitle(t.title())
                    .taskStatus(t.status())
                    .projectCode(t.projectCode())
                    .projectName(t.projectName())
                    .projectOwner(t.projectOwner())
                    .build())
        .toList();
  }

  @Override
  public Optional<IssueData> findById(final UUID id) {
    return this.issueRepository
        .findById(id)
        .map(i -> new IssueData(i.getId(), i.getNumber(), i.getTitle(), i.getStatus()));
  }

  private void assertCanModify(final UUID requesterId, final UUID authorId, final RepoRef repo) {

    if (requesterId.equals(authorId) || requesterId.equals(repo.ownerId())) {
      return;
    }

    if (this.collaboratorAccessPort.hasPermission(
        repo.id(), requesterId, CollaboratorPermission.ISSUE_MANAGE)) {
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
