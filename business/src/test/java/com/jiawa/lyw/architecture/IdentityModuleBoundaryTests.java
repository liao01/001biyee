package com.jiawa.lyw.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.jiawa.lyw", importOptions = ImportOption.DoNotIncludeTests.class)
class IdentityModuleBoundaryTests {
    @ArchTest
    static final ArchRule identityInfrastructureIsInternal = noClasses()
            .that().resideOutsideOfPackage("..identity..")
            .should().dependOnClassesThat().resideInAPackage("..identity.infrastructure..");

    @ArchTest
    static final ArchRule identityDomainIsFrameworkIndependent = classes()
            .that().resideInAPackage("..identity.domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..identity.domain..");

    @ArchTest
    static final ArchRule identityUseCasesHaveAnInterface = classes()
            .that().haveFullyQualifiedName("com.jiawa.lyw.identity.application.IdentityApplicationService")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule currentMemberHasAnInterface = classes()
            .that().haveFullyQualifiedName("com.jiawa.lyw.identity.application.CurrentMemberProvider")
            .should().beInterfaces();
}
