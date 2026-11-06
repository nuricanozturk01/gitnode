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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListPagingTest {

  private final List<Integer> all = IntStream.range(0, 25).boxed().toList();

  @Test
  void withoutParamsReturnsEverythingUpToMax() {
    final var res = ListPaging.respond(this.all, null, null, 10);
    assertEquals(10, res.getBody().size());
    assertEquals("25", res.getHeaders().getFirst(ListPaging.TOTAL_HEADER));
  }

  @Test
  void sizeIsCappedByMax() {
    assertEquals(10, ListPaging.respond(this.all, 0, 500, 10).getBody().size());
  }

  @Test
  void secondPageStartsAfterFirst() {
    assertEquals(List.of(5, 6, 7, 8, 9), ListPaging.respond(this.all, 1, 5, 100).getBody());
  }

  @Test
  void pageBeyondEndIsEmpty() {
    assertEquals(0, ListPaging.respond(this.all, 99, 10, 100).getBody().size());
  }
}
