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
package com.nuricanozturk.originhub.shared.cache;

public final class CacheNames {

  public static final String TREE = "repo:tree";
  public static final String BLOB = "repo:blob";
  public static final String LANGUAGES = "repo:languages";
  public static final String COMMITS = "repo:commits";
  public static final String BRANCHES = "repo:branches";
  public static final String TAGS = "repo:tags";

  public static final String REPO_META = "repo:meta";
  public static final String REPO_PR_OPEN_COUNT = "repo:pr:open-count";
  public static final String REPO_ISSUE_OPEN_COUNT = "repo:issue:open-count";

  public static final String SNIPPET_DETAIL = "snippet:detail";
  public static final String SNIPPET_LIST_PUBLIC = "snippet:list:public";

  public static final String ADMIN_STATS_OVERVIEW = "admin:stats:overview";
  public static final String ADMIN_STATS_REPO_ACTIVITY = "admin:stats:repo-activity";
  public static final String ADMIN_STATS_UPLOAD_ACTIVITY = "admin:stats:upload-activity";

  private CacheNames() {}
}
