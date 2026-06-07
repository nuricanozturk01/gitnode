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
package com.nuricanozturk.originhub.shared.errorhandling;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.BadRequestException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.TokenExpiredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.MissingRequestValueException;

@Slf4j
// @RestControllerAdvice
@RequiredArgsConstructor
@NullMarked
public class ErrorHandler {

  private static final String ERR_VALIDATION = "validationError";
  private static final String ERR_ERROR_OCCURRED = "errorOccurred";
  private static final String ERR_ILLEGAL_ARGUMENT = "Invalid method argument";

  @ExceptionHandler(MissingRequestValueException.class)
  ResponseEntity<String> handleException(
      final MissingRequestValueException ex, final HttpServletRequest request) {

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(Throwable.class)
  @Nullable ResponseEntity<Object> defaultExceptionHandler(
      final Throwable ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("An exception occurred", ex);
      return null;
    }

    if (ex instanceof final HttpClientErrorException exception) {
      log.error(exception.getResponseBodyAsString());
    }

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ERR_ERROR_OCCURRED);
  }

  @ExceptionHandler(AccessNotAllowedException.class)
  @Nullable ResponseEntity<String> handleException(
      final AccessNotAllowedException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Access not allowed", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  @Nullable ResponseEntity<String> handleException(
      final BadRequestException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Bad request", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(ConversionFailedException.class)
  @Nullable ResponseEntity<String> handleException(
      final ConversionFailedException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Conversion failed", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(ErrorOccurredException.class)
  @Nullable ResponseEntity<String> handleException(
      final ErrorOccurredException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("An error occurred", ex);

      return null;
    }

    final var exceptionMessage = ex.getMessage();
    final var messageText = exceptionMessage != null ? exceptionMessage : ERR_ERROR_OCCURRED;

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(messageText);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<String> handleException(
      final HttpMessageNotReadableException ex, final HttpServletRequest request) {

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @Nullable ResponseEntity<Void> handleException(
      final HttpRequestMethodNotSupportedException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Wrong request method", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  @Nullable ResponseEntity<Void> handleException(
      final HttpMediaTypeNotAcceptableException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Wrong mime type", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @ExceptionHandler(ItemNotFoundException.class)
  @Nullable ResponseEntity<String> handleException(
      final ItemNotFoundException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Item not found", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(TokenExpiredException.class)
  @Nullable ResponseEntity<String> handleException(
      final TokenExpiredException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("token expired", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(TooManyRequestsException.class)
  @Nullable ResponseEntity<String> handleException(
      final TooManyRequestsException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Rate limit exceeded", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  @Nullable ResponseEntity<String> handleException(
      final org.springframework.web.servlet.resource.NoResourceFoundException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Item not found", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @Nullable ResponseEntity<Void> handleException(
      final MethodArgumentNotValidException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug(ERR_ILLEGAL_ARGUMENT, ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @ExceptionHandler(MethodNotAllowedException.class)
  @Nullable ResponseEntity<Void> handleException(
      final MethodNotAllowedException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug(ERR_ILLEGAL_ARGUMENT, ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  @Nullable ResponseEntity<String> handleException(
      final MissingServletRequestParameterException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug(ERR_ILLEGAL_ARGUMENT, ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getParameterName());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  @Nullable ResponseEntity<String> handleException(
      final HttpMediaTypeNotSupportedException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug(ERR_ILLEGAL_ARGUMENT, ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ERR_VALIDATION);
  }

  @ExceptionHandler(ValidationException.class)
  @Nullable ResponseEntity<String> handleException(
      final ValidationException ex,
      final HttpServletRequest request,
      final @Nullable HttpServletResponse response) {

    if (response == null) {
      log.debug("Invalid request", ex);

      return null;
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.getMessage());
  }
}
