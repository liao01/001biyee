package com.jiawa.lyw.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.jiawa.lyw", importOptions = ImportOption.DoNotIncludeTests.class)
class ItineraryModuleBoundaryTests {
    @ArchTest
    static final ArchRule itineraryInfrastructureIsInternal = noClasses()
            .that().resideOutsideOfPackage("..itinerary..")
            .should().dependOnClassesThat().resideInAPackage("..itinerary.infrastructure..");

    @ArchTest
    static final ArchRule itineraryDomainIsFrameworkIndependent = classes()
            .that().resideInAPackage("..itinerary.domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..itinerary.domain..");

    @ArchTest
    static final ArchRule itineraryDoesNotUseLegacyGlobalMappers = noClasses()
            .that().resideInAPackage("..itinerary..")
            .should().dependOnClassesThat().resideInAPackage("com.jiawa.lyw.mapper..");

    @ArchTest
    static final ArchRule itineraryUseCasesHaveAnInterface = classes()
            .that().haveFullyQualifiedName(
                    "com.jiawa.lyw.itinerary.application.ItineraryApplicationService"
            )
            .should().beInterfaces();

    @ArchTest
    static final ArchRule itineraryAccessPolicyHasAnInterface = classes()
            .that().haveFullyQualifiedName(
                    "com.jiawa.lyw.itinerary.application.ItineraryAccessPolicy"
            )
            .should().beInterfaces();

    @ArchTest
    static final ArchRule transactionalItineraryServiceSupportsSpringProxying = classes()
            .that().haveFullyQualifiedName(
                    "com.jiawa.lyw.itinerary.application.DefaultItineraryApplicationService"
            )
            .should().notHaveModifier(JavaModifier.FINAL);
}
