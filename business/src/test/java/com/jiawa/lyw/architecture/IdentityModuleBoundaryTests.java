package com.jiawa.lyw.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.jiawa.lyw.controller.web.SmsCodeController;
import com.jiawa.lyw.service.SmsCodeService;
import com.jiawa.lyw.service.MemberService;
import com.jiawa.lyw.req.MemberRegisterReq;
import com.jiawa.lyw.req.MemberLoginReq;
import com.jiawa.lyw.req.MemberResetReq;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.jiawa.lyw", importOptions = ImportOption.DoNotIncludeTests.class)
class IdentityModuleBoundaryTests {
    @ArchTest
    static final ArchRule legacySmsCannotGainNewConsumers = noClasses()
            .that().doNotHaveFullyQualifiedName(SmsCodeController.class.getName())
            .and().doNotHaveFullyQualifiedName(SmsCodeService.class.getName())
            .should().dependOnClassesThat().haveFullyQualifiedName(SmsCodeService.class.getName());

    @ArchTest
    static final ArchRule legacyRegistrationCannotBeReconnected = noClasses()
            .should().callMethod(MemberService.class, "register", MemberRegisterReq.class);

    @ArchTest
    static final ArchRule legacyLoginCannotBeReconnected = noClasses()
            .should().callMethod(MemberService.class, "login", MemberLoginReq.class);

    @ArchTest
    static final ArchRule legacyPasswordResetCannotBeReconnected = noClasses()
            .should().callMethod(MemberService.class, "reset", MemberResetReq.class);

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
