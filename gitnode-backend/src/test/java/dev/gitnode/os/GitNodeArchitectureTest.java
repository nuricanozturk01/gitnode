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
package dev.gitnode.os;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@AnalyzeClasses(packages = "dev.gitnode.os", importOptions = ImportOption.DoNotIncludeTests.class)
public class GitNodeArchitectureTest {

  private static final @NonNull String MODULE_NAME = "dev.gitnode.os";

  @Test
  void moduleTest() {

    ApplicationModules.of(MODULE_NAME).verify();
  }

  @Test
  void writeModuleDocumentation() {

    new Documenter(ApplicationModules.of(MODULE_NAME))
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml();
  }

  /**
   * R1: web adapters talk to services, never straight to persistence. Existing violations are
   * frozen in {@code archunit_store}; new ones fail the build (see architecture plan, task 8).
   */
  @ArchTest
  static final ArchRule controllersDoNotUseRepositories =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideInAPackage("..controllers..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..repositories.."));

  /** R2: a module's published api must not leak JPA entities. */
  @ArchTest
  static final ArchRule apiDoesNotExposeEntities =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(jakarta.persistence.Entity.class);

  /** R4: layering - services never reach back into the web layer. */
  @ArchTest
  static final ArchRule servicesDoNotUseControllers =
      noClasses()
          .that()
          .resideInAPackage("..services..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..controllers..");

  /** R5: only the owning module touches its own persistence packages (no cross-module repos). */
  @ArchTest
  static final ArchRule persistenceStaysInsideModule =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideOutsideOfPackages(
                  "..repo..", "..identity..", "..githttp..", "..ssh..", "..profile..")
              .and()
              .resideInAnyPackage("dev.gitnode.os.(*)..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "dev.gitnode.os.repo.repositories..", "dev.gitnode.os.identity.repositories.."));

  /** R3: event contracts are plain records and know nothing about the modules. */
  @ArchTest
  static final ArchRule eventsAreIndependent =
      noClasses()
          .that()
          .resideInAPackage("dev.gitnode.os.events..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.gitnode.os.shared..",
              "dev.gitnode.os.pr..",
              "dev.gitnode.os.issue..",
              "dev.gitnode.os.auth..");
}
