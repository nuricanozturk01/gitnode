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
package dev.gitnode.os.wiki.dtos;

import java.time.Instant;
import org.jspecify.annotations.NullMarked;

/**
 * A wiki page. {@code blobSha} identifies this exact content; send it back as {@code baseSha} when
 * saving so a concurrent edit is detected instead of overwritten.
 */
@NullMarked
public record WikiPage(
    String slug,
    String title,
    String content,
    String blobSha,
    String commitSha,
    String lastEditor,
    Instant updatedAt) {}
