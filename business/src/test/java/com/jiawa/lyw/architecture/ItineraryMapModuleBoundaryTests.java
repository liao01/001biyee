package com.jiawa.lyw.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 防止供应商 DTO 或行程 MyBatis 实现反向渗入每日地图的公开边界。 */
@AnalyzeClasses(packages = "com.jiawa.lyw", importOptions = ImportOption.DoNotIncludeTests.class)
class ItineraryMapModuleBoundaryTests {
    @ArchTest
    static final ArchRule amapDtosStayInsideTheProviderAdapter = noClasses()
            .that().resideOutsideOfPackage("..itinerarymap.infrastructure.amap..")
            .should().dependOnClassesThat().resideInAPackage("..itinerarymap.infrastructure.amap.dto..");

    @ArchTest
    static final ArchRule mapModuleDoesNotDependOnItineraryInfrastructure = noClasses()
            .that().resideInAPackage("..itinerarymap..")
            .should().dependOnClassesThat().resideInAPackage("..itinerary.infrastructure..");

    @ArchTest
    static final ArchRule mapDomainIsFrameworkIndependent = classes()
            .that().resideInAPackage("..itinerarymap.domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..itinerarymap.domain..");

    @ArchTest
    static final ArchRule mapUseCaseHasAnInterface = classes()
            .that().haveFullyQualifiedName("com.jiawa.lyw.itinerarymap.application.ItineraryMapApplicationService")
            .should().beInterfaces();
}
