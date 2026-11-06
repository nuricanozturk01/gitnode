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

import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import dev.gitnode.os.shared.errorhandling.exceptions.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@NullMarked
public class ErrorHandler {

  private static final String ERR_VALIDATION = "validationError";
  private static final String ERR_ERROR_OCCURRED = "errorOccurred";
  private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

  /**
   * One place that shapes every error body (RFC 7807 problem+json). The code goes into {@code
   * detail} and, for clients that predate problem bodies, into {@code message} as well.
   */
  private static ResponseEntity<ProblemDetail> problem(
      final HttpStatus status, final @Nullable String code) {
    final var body = ProblemDetail.forStatus(status);
    if (code != null) {
      body.setDetail(code);
      body.setProperty("message", code);
    }
    final String traceId = MDC.get(RequestIdFilter.MDC_KEY);
    if (traceId != null) {
      body.setProperty("traceId", traceId);
    }
    return ResponseEntity.status(status).contentType(PROBLEM).body(body);
  }

  @ExceptionHandler(AccessNotAllowedException.class)
  ResponseEntity<ProblemDetail> forbidden(final AccessNotAllowedException ex) {
    log.debug("Access not allowed", ex);
    return problem(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(ItemAlreadyExistsException.class)
  ResponseEntity<ProblemDetail> conflict(final ItemAlreadyExistsException ex) {
    log.debug("Item already exists", ex);
    return problem(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler({
    BadRequestException.class,
    ValidationException.class,
    HttpMessageNotReadableException.class,
    MissingRequestValueException.class
  })
  ResponseEntity<ProblemDetail> badRequest(final Exception ex) {
    log.debug("Bad request", ex);
    return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler({
    ConversionFailedException.class,
    ItemNotFoundException.class,
    NoResourceFoundException.class
  })
  ResponseEntity<ProblemDetail> notFound(final Exception ex) {
    log.debug("Item not found", ex);
    return problem(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ErrorOccurredException.class)
  ResponseEntity<ProblemDetail> errorOccurred(final ErrorOccurredException ex) {
    log.debug("An error occurred", ex);
    final var message = ex.getMessage();
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, message != null ? message : ERR_ERROR_OCCURRED);
  }

  @ExceptionHandler(TokenExpiredException.class)
  ResponseEntity<ProblemDetail> unauthorized(final TokenExpiredException ex) {
    log.debug("Token expired", ex);
    return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(TooManyRequestsException.class)
  ResponseEntity<ProblemDetail> tooManyRequests(final TooManyRequestsException ex) {
    log.debug("Rate limit exceeded", ex);
    return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
  }

  @ExceptionHandler({
    HttpRequestMethodNotSupportedException.class,
    HttpMediaTypeNotAcceptableException.class,
    MethodArgumentNotValidException.class,
    MethodNotAllowedException.class
  })
  ResponseEntity<ProblemDetail> badRequestNoDetail(final Exception ex) {
    log.debug("Invalid request", ex);
    return problem(HttpStatus.BAD_REQUEST, null);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ProblemDetail> missingParameter(final MissingServletRequestParameterException ex) {
    log.debug("Missing parameter", ex);
    return problem(HttpStatus.BAD_REQUEST, ex.getParameterName());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> unsupportedMediaType(final HttpMediaTypeNotSupportedException ex) {
    log.debug("Unsupported media type", ex);
    return problem(HttpStatus.BAD_REQUEST, ERR_VALIDATION);
  }

  /** Last resort for anything not handled above. */
  @ExceptionHandler(Throwable.class)
  @Nullable ResponseEntity<ProblemDetail> unhandled(
      final Throwable ex, final HttpServletRequest request) {
    if (ex instanceof AsyncRequestNotUsableException) {
      // The client went away while a stream (SSE) was open; nothing to answer and not an error.
      log.debug("Client disconnected on {} {}", request.getMethod(), request.getRequestURI());
      return null;
    }
    if (ex instanceof final HttpClientErrorException exception) {
      log.error(exception.getResponseBodyAsString());
    }
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, ERR_ERROR_OCCURRED);
  }
}
