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
package dev.gitnode.os.shared.pagination;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

/**
 * Bounds plain-list endpoints without changing their response shape. {@code page}/{@code size} are
 * optional; {@code size} is capped by the configured maximum and the total is reported in {@value
 * #TOTAL_HEADER}.
 */
public final class ListPaging {

  public static final String TOTAL_HEADER = "X-Total-Count";

  private ListPaging() {}

  public static int size(final @Nullable Integer size, final int max) {
    return size == null ? max : Math.clamp(size, 1, max);
  }

  public static int offset(final @Nullable Integer page, final int size) {
    return page == null ? 0 : (int) Math.min((long) Math.max(page, 0) * size, Integer.MAX_VALUE);
  }

  /** Slices an already loaded list. */
  public static <T> ResponseEntity<List<T>> respond(
      final List<T> all,
      final @Nullable Integer page,
      final @Nullable Integer size,
      final int max) {
    final int limit = size(size, max);
    final int from = Math.min(offset(page, limit), all.size());
    final int to = (int) Math.min((long) from + limit, all.size());
    return withTotal(all.subList(from, to), all.size());
  }

  public static <T> ResponseEntity<List<T>> withTotal(final List<T> items, final long total) {
    return ResponseEntity.ok().header(TOTAL_HEADER, Long.toString(total)).body(items);
  }
}
