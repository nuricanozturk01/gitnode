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
package dev.gitnode.os.actions.services;

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/** Resolves a job's {@code needs} against the jobs of a run and aggregates their results. */
@NullMarked
final class JobNeeds {

  static final String SUCCESS = "success";
  static final String FAILURE = "failure";
  static final String CANCELLED = "cancelled";
  static final String SKIPPED = "skipped";

  private JobNeeds() {}

  /** All jobs a {@code needs} entry refers to; a matrix job expands to several. */
  static List<WorkflowJob> jobsFor(final List<WorkflowJob> all, final String need) {
    return all.stream()
        .filter(j -> need.equals(j.getJobKey()) || need.equals(j.getName()))
        .toList();
  }

  static boolean isTerminal(final WorkflowJob job) {
    final var s = job.getStatus();
    return s == WorkflowJobStatus.SUCCESS
        || s == WorkflowJobStatus.FAILURE
        || s == WorkflowJobStatus.CANCELLED
        || s == WorkflowJobStatus.SKIPPED;
  }

  /**
   * One result for a group of jobs: failure beats cancelled beats skipped beats success. An empty
   * group (the needed job does not exist) counts as skipped.
   */
  static String aggregate(final List<WorkflowJob> jobs) {
    if (jobs.isEmpty()) {
      return SKIPPED;
    }
    if (jobs.stream().anyMatch(j -> j.getStatus() == WorkflowJobStatus.FAILURE)) {
      return FAILURE;
    }
    if (jobs.stream().anyMatch(j -> j.getStatus() == WorkflowJobStatus.CANCELLED)) {
      return CANCELLED;
    }
    if (jobs.stream().anyMatch(j -> j.getStatus() == WorkflowJobStatus.SKIPPED)) {
      return SKIPPED;
    }
    return SUCCESS;
  }

  /**
   * The status success()/failure()/cancelled() see for a job whose needs have all finished: failure
   * if any need failed, cancelled if any was cancelled, skipped if any was skipped (so success() is
   * false and the job is skipped), otherwise success.
   */
  static String statusFor(final List<String> needResults) {
    if (needResults.contains(FAILURE)) {
      return FAILURE;
    }
    if (needResults.contains(CANCELLED)) {
      return CANCELLED;
    }
    if (needResults.contains(SKIPPED)) {
      return SKIPPED;
    }
    return SUCCESS;
  }
}
