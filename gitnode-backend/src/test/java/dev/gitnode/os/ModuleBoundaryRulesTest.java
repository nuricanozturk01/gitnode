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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.Map;
import java.util.Set;

/**
 * Module boundary rules (quality-plan A1). Every rule is frozen: existing violations are recorded
 * in {@code archunit_store} and may only shrink; any new violation fails the build.
 */
@AnalyzeClasses(packages = "dev.gitnode.os", importOptions = ImportOption.DoNotIncludeTests.class)
public class ModuleBoundaryRulesTest {

  private static final String ROOT = "dev.gitnode.os.";
  private static final Set<String> INTERNAL_PACKAGES =
      Set.of(
          "services", "entities", "repositories", "mappers", "controllers", "configs", "listeners");
  private static final Set<String> NOT_DOMAIN = Set.of("shared", "events");

  /** Returns the module segment of a class in {@code dev.gitnode.os.<module>...}, else null. */
  private static String module(final JavaClass c) {
    final String pkg = c.getPackageName();
    if (!pkg.startsWith(ROOT)) {
      return null;
    }
    final String rest = pkg.substring(ROOT.length());
    final int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  /** Returns the sub-package directly under the module, else null. */
  private static String layer(final JavaClass c) {
    final String pkg = c.getPackageName();
    final String module = module(c);
    if (module == null || pkg.length() <= ROOT.length() + module.length()) {
      return null;
    }
    final String rest = pkg.substring(ROOT.length() + module.length() + 1);
    final int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  /**
   * R6: code outside a module may only use its api/events/named interfaces, never its internals.
   */
  @ArchTest
  static final ArchRule modulesDoNotUseForeignInternals =
      classes()
          .should(
              new ArchCondition<JavaClass>("not depend on another module's internal packages") {
                @Override
                public void check(final JavaClass origin, final ConditionEvents events) {
                  final String from = module(origin);
                  if (from == null) {
                    return;
                  }
                  for (final Dependency dep : origin.getDirectDependenciesFromSelf()) {
                    final JavaClass target = dep.getTargetClass();
                    final String to = module(target);
                    if (to != null
                        && !to.equals(from)
                        && !NOT_DOMAIN.contains(to)
                        && INTERNAL_PACKAGES.contains(layer(target))) {
                      events.add(SimpleConditionEvent.violated(dep, dep.getDescription()));
                    }
                  }
                }
              });

  /** R7: a published api exposes neither entities, repositories nor controllers. */
  @ArchTest
  static final ArchRule apiIsFreeOfPersistenceAndWeb =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..entities..", "..repositories..", "..controllers..");

  /** R8: no cyclic dependencies between modules. */
  @ArchTest
  static final ArchRule modulesAreFreeOfCycles =
      SlicesRuleDefinition.slices().matching("dev.gitnode.os.(*)..").should().beFreeOfCycles();

  /** R9: the shared core knows no domain module. */
  @ArchTest
  static final ArchRule sharedDoesNotDependOnDomainModules =
      noClasses()
          .that()
          .resideInAPackage("dev.gitnode.os.shared..")
          .should(
              new ArchCondition<JavaClass>("depend on a domain module") {
                @Override
                public void check(final JavaClass origin, final ConditionEvents events) {
                  for (final Dependency dep : origin.getDirectDependenciesFromSelf()) {
                    final String to = module(dep.getTargetClass());
                    if (to != null && !NOT_DOMAIN.contains(to)) {
                      events.add(SimpleConditionEvent.satisfied(dep, dep.getDescription()));
                    }
                  }
                }
              });

  /**
   * R10: transaction boundaries live in services (or their adapters, event listeners/publishers and
   * Spring Data repositories).
   */
  @ArchTest
  static final ArchRule transactionalOnlyInServices =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "..services..", "..adapters..", "..listeners..", "..repositories..", "..events..")
          .should()
          .beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
          .orShould()
          .dependOnClassesThat()
          .areAssignableTo(org.springframework.transaction.annotation.Transactional.class);

  /** R11: constructor injection only. */
  @ArchTest
  static final ArchRule noFieldInjection =
      fields()
          .should()
          .notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class);

  /** R12 (D11): repo's published dtos are plain value objects, no entities or repositories. */
  @ArchTest
  static final ArchRule dtosAreValueObjects =
      noClasses()
          .that()
          .resideInAPackage("dev.gitnode.os.repo.dtos..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..entities..", "..repositories..");

  /**
   * R13: module layers. A module may depend only on modules in a strictly lower layer, so the
   * dependency graph stays a DAG with a known direction (see docs/adr once written; quality-plan
   * A5). A new module must be placed in this table.
   */
  private static final Map<String, Integer> LAYERS =
      Map.ofEntries(
          Map.entry("identity", 0),
          Map.entry("auth", 1),
          Map.entry("repo", 1),
          Map.entry("branch", 2),
          Map.entry("commit", 2),
          Map.entry("migration", 2),
          Map.entry("notification", 2),
          Map.entry("profile", 2),
          Map.entry("snippet", 2),
          Map.entry("tag", 2),
          Map.entry("team", 2),
          Map.entry("wiki", 2),
          Map.entry("collaborator", 3),
          Map.entry("discussion", 3),
          Map.entry("filecomment", 3),
          Map.entry("githttp", 3),
          Map.entry("issue", 3),
          Map.entry("ssh", 3),
          Map.entry("tree", 3),
          Map.entry("actions", 4),
          Map.entry("pr", 4),
          Map.entry("ai", 5),
          Map.entry("task", 5),
          Map.entry("webhook", 6),
          Map.entry("admin", 7));

  @ArchTest
  static final ArchRule modulesOnlyDependDownwards =
      classes()
          .should(
              new ArchCondition<JavaClass>("depend only on modules in a lower layer") {
                @Override
                public void check(final JavaClass origin, final ConditionEvents events) {
                  final String from = module(origin);
                  if (from == null || NOT_DOMAIN.contains(from)) {
                    return;
                  }
                  final Integer fromLayer = LAYERS.get(from);
                  if (fromLayer == null) {
                    events.add(
                        SimpleConditionEvent.violated(
                            origin, "module '" + from + "' has no layer"));
                    return;
                  }
                  for (final Dependency dep : origin.getDirectDependenciesFromSelf()) {
                    final String to = module(dep.getTargetClass());
                    if (to == null || to.equals(from) || NOT_DOMAIN.contains(to)) {
                      continue;
                    }
                    final Integer toLayer = LAYERS.get(to);
                    if (toLayer == null || toLayer >= fromLayer) {
                      events.add(SimpleConditionEvent.violated(dep, dep.getDescription()));
                    }
                  }
                }
              });
}
