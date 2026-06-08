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
package dev.gitnode.os.actions.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.gitnode.os.actions.dtos.JobCancelledData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerMessage(String type, @Nullable Object data) {

  public static ServerMessage ping() {
    return new ServerMessage("PING", null);
  }

  public static ServerMessage jobAssigned(final Object jobPayload) {

    return new ServerMessage("JOB_ASSIGNED", new JobAssignedData(jobPayload));
  }

  public record JobAssignedData(Object job) {}

  public static ServerMessage jobCancelled(final String jobId) {

    return new ServerMessage("JOB_CANCELLED", new JobCancelledData(jobId));
  }
}
