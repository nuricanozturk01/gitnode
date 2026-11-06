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
import com.tngtech.archunit.library.freeze.FreezingArchRule;
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
      FreezingArchRule.freeze(
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
                  }));

  /** R7: a published api exposes neither entities, repositories nor controllers. */
  @ArchTest
  static final ArchRule apiIsFreeOfPersistenceAndWeb =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideInAPackage("..api..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..entities..", "..repositories..", "..controllers.."));

  /** R8: no cyclic dependencies between modules. */
  @ArchTest
  static final ArchRule modulesAreFreeOfCycles =
      FreezingArchRule.freeze(
          SlicesRuleDefinition.slices().matching("dev.gitnode.os.(*)..").should().beFreeOfCycles());

  /** R9: the shared core knows no domain module. */
  @ArchTest
  static final ArchRule sharedDoesNotDependOnDomainModules =
      FreezingArchRule.freeze(
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
                  }));

  /**
   * R10: transaction boundaries live in services (or their adapters, event listeners/publishers and
   * Spring Data repositories).
   */
  @ArchTest
  static final ArchRule transactionalOnlyInServices =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideOutsideOfPackages(
                  "..services..", "..adapters..", "..listeners..", "..repositories..", "..events..")
              .should()
              .beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
              .orShould()
              .dependOnClassesThat()
              .areAssignableTo(org.springframework.transaction.annotation.Transactional.class));

  /** R11: constructor injection only. */
  @ArchTest
  static final ArchRule noFieldInjection =
      FreezingArchRule.freeze(
          fields()
              .should()
              .notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class));

  /** R12 (D11): repo's published dtos are plain value objects, no entities or repositories. */
  @ArchTest
  static final ArchRule dtosAreValueObjects =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideInAPackage("dev.gitnode.os.repo.dtos..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..entities..", "..repositories.."));
}
