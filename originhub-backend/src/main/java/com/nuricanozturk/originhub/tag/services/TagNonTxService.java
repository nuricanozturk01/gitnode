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
package com.nuricanozturk.originhub.tag.services;

import com.nuricanozturk.originhub.shared.cache.CacheNames;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.tag.dtos.CreateTagForm;
import com.nuricanozturk.originhub.tag.dtos.TagInfo;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class TagNonTxService {

  private static final int DEFAULT_SHORT_SHA_LENGTH = 7;
  private static final String TAG_SYSTEM_USER = "OriginHub";
  private static final String TAG_SYSTEM_EMAIL = "noreply@originhub.local";

  private final GitProvider gitProvider;
  private final ReleaseTxService releaseTxService;

  @Cacheable(cacheNames = CacheNames.TAGS, key = "#owner + ':' + #repoName")
  public List<TagInfo> getAll(final String owner, final String repoName) throws IOException {

    final var repo = this.releaseTxService.findRepo(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var refs = gitRepo.getRefDatabase().getRefsByPrefix(Constants.R_TAGS);

      if (refs.isEmpty()) {
        return List.of();
      }

      return refs.stream()
          .map(ref -> this.buildTagInfo(gitRepo, walk, ref, repo.getId()))
          .sorted((a, b) -> b.taggerDate().compareTo(a.taggerDate()))
          .toList();
    }
  }

  public TagInfo get(final String owner, final String repoName, final String tagName)
      throws IOException {

    final var repo = this.releaseTxService.findRepo(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var ref = this.getTagRef(gitRepo, tagName);
      return this.buildTagInfo(gitRepo, walk, ref, repo.getId());
    }
  }

  @CacheEvict(cacheNames = CacheNames.TAGS, key = "#owner + ':' + #repoName")
  public TagInfo create(final String owner, final String repoName, final CreateTagForm form)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      this.checkTagExists(gitRepo, form.getName());

      final var targetId = this.resolveTarget(gitRepo, form.getSha(), owner, repoName);

      if (form.getMessage() != null && !form.getMessage().isBlank()) {
        this.createAnnotatedTag(gitRepo, form.getName(), targetId, form.getMessage());
      } else {
        this.createLightweightTag(gitRepo, form.getName(), targetId);
      }
    }

    return this.get(owner, repoName, form.getName());
  }

  @CacheEvict(cacheNames = CacheNames.TAGS, key = "#owner + ':' + #repoName")
  public void delete(final String owner, final String repoName, final String tagName)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      this.checkTagNonExists(gitRepo, tagName);

      final var refUpdate = gitRepo.updateRef(Constants.R_TAGS + tagName);
      refUpdate.setForceUpdate(true);
      final var result = refUpdate.delete();
      this.checkDeleteResult(result, tagName);
    }

    this.releaseTxService.deleteByTagName(owner, repoName, tagName);
  }

  private void createAnnotatedTag(
      final Repository gitRepo, final String name, final ObjectId targetId, final String message)
      throws IOException {

    try (final var inserter = gitRepo.newObjectInserter()) {
      final var tagger =
          new PersonIdent(TAG_SYSTEM_USER, TAG_SYSTEM_EMAIL, Instant.now(), ZoneOffset.UTC);

      final var tagBuilder = new TagBuilder();
      tagBuilder.setTag(name);
      tagBuilder.setObjectId(targetId, Constants.OBJ_COMMIT);
      tagBuilder.setMessage(message.endsWith("\n") ? message : message + "\n");
      tagBuilder.setTagger(tagger);

      final var tagObjectId = inserter.insert(tagBuilder);
      inserter.flush();

      final var refUpdate = gitRepo.updateRef(Constants.R_TAGS + name);
      refUpdate.setNewObjectId(tagObjectId);
      final var result = refUpdate.update();
      this.checkCreateResult(result, name);
    }
  }

  private void createLightweightTag(
      final Repository gitRepo, final String name, final ObjectId targetId) throws IOException {

    final var refUpdate = gitRepo.updateRef(Constants.R_TAGS + name);
    refUpdate.setNewObjectId(targetId);
    final var result = refUpdate.update();
    this.checkCreateResult(result, name);
  }

  private ObjectId resolveTarget(
      final Repository gitRepo,
      final @Nullable String sha,
      final String owner,
      final String repoName)
      throws IOException {

    if (sha != null && !sha.isBlank()) {
      final var objectId = gitRepo.resolve(sha);

      if (objectId == null) {
        throw new ItemNotFoundException("commitNotFound: " + sha);
      }

      return objectId;
    }

    final var dbRepo = this.releaseTxService.findRepo(owner, repoName);
    final var headRef = gitRepo.findRef(Constants.R_HEADS + dbRepo.getDefaultBranch());

    if (headRef == null) {
      throw new ErrorOccurredException("emptyRepo: no commits on default branch");
    }

    return headRef.getObjectId();
  }

  private TagInfo buildTagInfo(
      final Repository gitRepo, final RevWalk walk, final Ref ref, final UUID repoId) {

    try {
      final var peeledRef = gitRepo.getRefDatabase().peel(ref);
      final var isAnnotated = peeledRef.getPeeledObjectId() != null;
      final var commitId = isAnnotated ? peeledRef.getPeeledObjectId() : ref.getObjectId();

      final var commit = walk.parseCommit(commitId);
      final var tagName = ref.getName().replace(Constants.R_TAGS, "");
      final var commitSha = commitId.getName();

      final var tagMessage = isAnnotated ? this.extractTagMessage(walk, ref) : null;
      final var taggerIdent =
          isAnnotated ? this.extractTaggerIdent(walk, ref) : commit.getAuthorIdent();

      final var release =
          this.releaseTxService.findByRepoIdAndTagName(repoId, tagName).orElse(null);

      return TagInfo.builder()
          .name(tagName)
          .commitSha(commitSha)
          .commitShortSha(commitSha.substring(0, DEFAULT_SHORT_SHA_LENGTH))
          .commitMessage(commit.getShortMessage())
          .taggerName(taggerIdent.getName())
          .taggerDate(taggerIdent.getWhenAsInstant())
          .tagMessage(tagMessage)
          .isAnnotated(isAnnotated)
          .release(release)
          .build();

    } catch (final IOException ex) {
      log.warn("Failed building tag info: ", ex);
      throw new ErrorOccurredException("Failed building tag info: " + ex.getMessage());
    }
  }

  private @Nullable String extractTagMessage(final RevWalk walk, final Ref ref) {
    try {
      final var revObject = walk.parseAny(ref.getObjectId());
      if (revObject instanceof RevTag revTag) {
        walk.parseBody(revTag);
        return revTag.getFullMessage();
      }
      return null;
    } catch (final IOException ex) {
      log.warn("Could not extract tag message: ", ex);
      return null;
    }
  }

  private PersonIdent extractTaggerIdent(final RevWalk walk, final Ref ref) {
    try {
      final var revObject = walk.parseAny(ref.getObjectId());
      if (revObject instanceof RevTag revTag) {
        walk.parseBody(revTag);
        return revTag.getTaggerIdent();
      }
    } catch (final IOException ex) {
      log.warn("Could not extract tagger ident: ", ex);
    }
    return new PersonIdent(TAG_SYSTEM_USER, TAG_SYSTEM_EMAIL);
  }

  private Ref getTagRef(final Repository gitRepo, final String tagName) throws IOException {
    final var ref = gitRepo.findRef(Constants.R_TAGS + tagName);
    if (ref == null) {
      throw new ItemNotFoundException("tagNotFound: " + tagName);
    }
    return ref;
  }

  private void checkTagExists(final Repository gitRepo, final String name) throws IOException {
    if (gitRepo.findRef(Constants.R_TAGS + name) != null) {
      throw new ItemAlreadyExistsException("tagAlreadyExists: " + name);
    }
  }

  private void checkTagNonExists(final Repository gitRepo, final String name) throws IOException {
    if (gitRepo.findRef(Constants.R_TAGS + name) == null) {
      throw new ItemNotFoundException("tagNotFound: " + name);
    }
  }

  private void checkCreateResult(final RefUpdate.Result result, final String tagName) {
    if (result != RefUpdate.Result.NEW) {
      throw new ErrorOccurredException("Failed to create tag '%s': %s".formatted(tagName, result));
    }
  }

  private void checkDeleteResult(final RefUpdate.Result result, final String tagName) {
    if (result != RefUpdate.Result.FORCED) {
      throw new ErrorOccurredException("Failed to delete tag '%s': %s".formatted(tagName, result));
    }
  }
}
