package com.jiawa.lyw.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.jiawa.lyw", importOptions = ImportOption.DoNotIncludeTests.class)
class ItineraryPlanningModuleBoundaryTests {
    @ArchTest
    static final ArchRule planningDoesNotUseItineraryInfrastructure = noClasses()
            .that().resideInAPackage("..itineraryplanning..")
            .should().dependOnClassesThat().resideInAPackage("..itinerary.infrastructure..");

    @ArchTest
    static final ArchRule difyAdapterCannotWriteTheFormalItinerary = noClasses()
            .that().haveFullyQualifiedName(
                    "com.jiawa.lyw.itineraryplanning.infrastructure.DifyItineraryPlannerGateway"
            )
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.jiawa.lyw.itinerary.application.ItineraryApplicationService"
            );

    @ArchTest
    static final ArchRule difyAdapterCannotAccessPlanningPersistence = noClasses()
            .that().haveFullyQualifiedName(
                    "com.jiawa.lyw.itineraryplanning.infrastructure.DifyItineraryPlannerGateway"
            )
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.jiawa.lyw.itineraryplanning.application.PlanningRepository"
            );
}
