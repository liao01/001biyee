package com.jiawa.lyw.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
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
            .should().dependOnClassesThat().resideInAPackage("..mapper..");
}
