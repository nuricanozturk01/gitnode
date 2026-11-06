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
package dev.gitnode.os.shared.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import dev.gitnode.os.shared.errorhandling.exceptions.TooManyRequestsException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class ErrorHandlerTest {

  private final ErrorHandler handler = new ErrorHandler();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  static Stream<Arguments> exceptions() {
    return Stream.of(
        Arguments.of(new AccessNotAllowedException("forbiddenCode"), HttpStatus.FORBIDDEN),
        Arguments.of(new ItemAlreadyExistsException("existsCode"), HttpStatus.CONFLICT),
        Arguments.of(new BadRequestException("badCode"), HttpStatus.BAD_REQUEST),
        Arguments.of(new ItemNotFoundException("missingCode"), HttpStatus.NOT_FOUND),
        Arguments.of(new TokenExpiredException("tokenCode"), HttpStatus.UNAUTHORIZED),
        Arguments.of(new TooManyRequestsException("slowDown"), HttpStatus.TOO_MANY_REQUESTS),
        Arguments.of(new ErrorOccurredException("failedCode"), HttpStatus.INTERNAL_SERVER_ERROR));
  }

  @ParameterizedTest
  @MethodSource("exceptions")
  void mapsEveryDomainExceptionToItsStatusAndCode(
      final RuntimeException ex, final HttpStatus status) {
    final var request = new MockHttpServletRequest("GET", "/x");
    final var response = this.respond(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    final var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(status.value());
    assertThat(body.getDetail()).isEqualTo(ex.getMessage());
    assertThat(body.getProperties()).containsEntry("message", ex.getMessage());
  }

  @Test
  void carriesTheRequestIdAsTraceId() {
    MDC.put(RequestIdFilter.MDC_KEY, "trace-12345");
    final var response =
        this.respond(new ItemNotFoundException("x"), new MockHttpServletRequest("GET", "/x"));

    assertThat(response.getBody().getProperties()).containsEntry("traceId", "trace-12345");
  }

  @Test
  void unknownFailuresAreOpaque500() {
    final var response =
        this.respond(
            new IllegalStateException("internal detail"), new MockHttpServletRequest("GET", "/x"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getDetail()).isEqualTo("errorOccurred");
  }

  @Test
  void clientDisconnectOnStreamAnswersNothing() {
    final var response =
        this.handler.unhandled(
            new AsyncRequestNotUsableException("gone"), new MockHttpServletRequest("GET", "/x"));

    assertThat(response).isNull();
  }

  private org.springframework.http.ResponseEntity<org.springframework.http.ProblemDetail> respond(
      final Throwable ex, final MockHttpServletRequest request) {
    if (ex instanceof AccessNotAllowedException e) {
      return this.handler.forbidden(e);
    }
    if (ex instanceof ItemAlreadyExistsException e) {
      return this.handler.conflict(e);
    }
    if (ex instanceof BadRequestException e) {
      return this.handler.badRequest(e);
    }
    if (ex instanceof ItemNotFoundException e) {
      return this.handler.notFound(e);
    }
    if (ex instanceof TokenExpiredException e) {
      return this.handler.unauthorized(e);
    }
    if (ex instanceof TooManyRequestsException e) {
      return this.handler.tooManyRequests(e);
    }
    if (ex instanceof ErrorOccurredException e) {
      return this.handler.errorOccurred(e);
    }
    return this.handler.unhandled(ex, request);
  }
}
