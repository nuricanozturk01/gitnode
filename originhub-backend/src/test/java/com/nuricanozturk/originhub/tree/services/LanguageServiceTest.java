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
package com.nuricanozturk.originhub.tree.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuricanozturk.originhub.tree.utils.LanguageExtensionUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LanguageExtensionUtils unit tests")
class LanguageServiceTest {

  @Test
  @DisplayName("detectLanguage returns java for .java files")
  void detectLanguage_returnsJava_forJavaFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("Main.java")).isEqualTo("java");
  }

  @Test
  @DisplayName("detectLanguage returns typescript for .ts files")
  void detectLanguage_returnsTypescript_forTsFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("app.ts")).isEqualTo("typescript");
  }

  @Test
  @DisplayName("detectLanguage returns typescript for .tsx files")
  void detectLanguage_returnsTypescript_forTsxFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("component.tsx")).isEqualTo("typescript");
  }

  @Test
  @DisplayName("detectLanguage returns python for .py files")
  void detectLanguage_returnsPython_forPyFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("script.py")).isEqualTo("python");
  }

  @Test
  @DisplayName("detectLanguage returns plaintext for unknown extension")
  void detectLanguage_returnsPlaintext_forUnknownExtension() {
    assertThat(LanguageExtensionUtils.detectLanguage("file.xyz")).isEqualTo("plaintext");
  }

  @Test
  @DisplayName("detectLanguage returns plaintext for null filename")
  void detectLanguage_returnsPlaintext_forNullFilename() {
    assertThat(LanguageExtensionUtils.detectLanguage(null)).isEqualTo("plaintext");
  }

  @Test
  @DisplayName("detectLanguage returns makefile for Makefile")
  void detectLanguage_returnsMakefile_forMakefile() {
    assertThat(LanguageExtensionUtils.detectLanguage("Makefile")).isEqualTo("makefile");
  }

  @Test
  @DisplayName("detectLanguage is case-insensitive for extensions")
  void detectLanguage_caseInsensitive_forExtensions() {
    assertThat(LanguageExtensionUtils.detectLanguage("Main.JAVA")).isEqualTo("java");
    assertThat(LanguageExtensionUtils.detectLanguage("App.TS")).isEqualTo("typescript");
  }

  @Test
  @DisplayName("detectLanguage returns go for .go files")
  void detectLanguage_returnsGo_forGoFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("main.go")).isEqualTo("go");
  }

  @Test
  @DisplayName("detectLanguage returns rust for .rs files")
  void detectLanguage_returnsRust_forRsFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("lib.rs")).isEqualTo("rust");
  }

  @Test
  @DisplayName("detectLanguage returns sql for .sql files")
  void detectLanguage_returnsSql_forSqlFiles() {
    assertThat(LanguageExtensionUtils.detectLanguage("V001__init.sql")).isEqualTo("sql");
  }
}
