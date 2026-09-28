package com.deeprunner.docsearch.architecture;

import com.deeprunner.docsearch.repository.DocumentRepository;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.deeprunner.docsearch", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule noUntenantedFindById =
        noClasses()
            .should().callMethod(DocumentRepository.class, "findById", Object.class)
            .because("Untenanted findById is a cross-tenant security risk; callers must use findByTenantIdAndIdAndDeletedAtIsNull");

    @ArchTest
    public static final ArchRule controllersShouldNotDependOnRepositories =
        noClasses().that().resideInAPackage("..web.controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("Controllers must delegate through domain services and not query repositories directly");

    @ArchTest
    public static final ArchRule layeredArchitecture =
        classes().that().resideInAPackage("..domain.entity..")
            .should().onlyBeAccessed().byAnyPackage("..domain..", "..repository..", "..service..")
            .because("Entities must not be directly accessed from the web presentation layer");
}
