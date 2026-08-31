package com.redhat.migrationtoolkit.rhcl;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import com.tngtech.archunit.core.domain.JavaModifier;

@AnalyzeClasses(
        packages = "com.redhat.migrationtoolkit.rhcl",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    // ── Layered architecture ──────────────────────────────────────────────────

    @ArchTest
    static final ArchRule layeredArchitecture_isRespected = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("com.redhat.migrationtoolkit.rhcl.controller..")
            .layer("Service").definedBy("com.redhat.migrationtoolkit.rhcl.service..")
            .layer("Client").definedBy("com.redhat.migrationtoolkit.rhcl.client..")
            .layer("Model").definedBy("com.redhat.migrationtoolkit.rhcl.model..")
            .layer("Entity").definedBy("com.redhat.migrationtoolkit.rhcl.entity..")
            .layer("DTO").definedBy("com.redhat.migrationtoolkit.rhcl.dto..")
            .layer("Util").definedBy("com.redhat.migrationtoolkit.rhcl.util..")
            .layer("Exception").definedBy("com.redhat.migrationtoolkit.rhcl.exception..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Client").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Entity").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Util").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Exception").mayOnlyBeAccessedByLayers("Controller", "Service", "Exception")
            .whereLayer("Model").mayOnlyBeAccessedByLayers("Controller", "Service", "Client", "DTO");

    // ── Controller rules ──────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule controllers_havePathAnnotation = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..")
            .and().areNotInterfaces()
            .and().areNotAnnotations()
            .and().arePublic()
            .and().areNotMemberClasses()
            .should().beAnnotatedWith(jakarta.ws.rs.Path.class);

    @ArchTest
    static final ArchRule controllers_doNotAccessClientDirectly = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.redhat.migrationtoolkit.rhcl.client..");

    @ArchTest
    static final ArchRule controllers_areInCorrectPackage = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..");

    // ── Service rules ─────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule services_haveApplicationScopedAnnotation = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service..")
            .and().resideOutsideOfPackage("com.redhat.migrationtoolkit.rhcl.service.conversion..")
            .and().resideOutsideOfPackage("com.redhat.migrationtoolkit.rhcl.service.generator.contributor..")
            .and().areNotInterfaces()
            .and().areNotMemberClasses()
            .and().areNotEnums()
            .and().doNotHaveModifier(JavaModifier.ABSTRACT)
            .and().areTopLevelClasses()
            .and().haveSimpleNameNotEndingWith("Factory")
            .should().beAnnotatedWith(jakarta.enterprise.context.ApplicationScoped.class);

    @ArchTest
    static final ArchRule services_areInCorrectPackage = classes()
            .that().haveSimpleNameEndingWith("Service")
            .and().areTopLevelClasses()
            .should().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service..")
            .orShould().resideInAPackage("com.redhat.migrationtoolkit.rhcl.model..");

    @ArchTest
    static final ArchRule services_doNotHavePathAnnotation = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service..")
            .should().beAnnotatedWith(jakarta.ws.rs.Path.class);

    // ── Entity rules ──────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule entities_haveEntityAnnotation = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.entity..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith(jakarta.persistence.Entity.class);

    @ArchTest
    static final ArchRule entities_areInCorrectPackage = classes()
            .that().haveSimpleNameEndingWith("Entity")
            .should().resideInAPackage("com.redhat.migrationtoolkit.rhcl.entity..");

    @ArchTest
    static final ArchRule entities_doNotDependOnControllers = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.entity..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..");

    // ── Client rules ──────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule clients_areInterfaces = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.client..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule clients_areInCorrectPackage = classes()
            .that().haveSimpleNameEndingWith("Client")
            .and().areNotAnnotations()
            .should().resideInAPackage("com.redhat.migrationtoolkit.rhcl.client..");

    // ── Model rules ───────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule models_doNotDependOnControllerOrService = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.redhat.migrationtoolkit.rhcl.controller..",
                    "com.redhat.migrationtoolkit.rhcl.service.."
            );

    // ── Util rules ────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule utilClasses_doNotDependOnControllers = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.util..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..");

    @ArchTest
    static final ArchRule conversionPackage_doesNotDependOnControllers = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service.conversion..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..");

    @ArchTest
    static final ArchRule generators_mayDependOnConversionAndModel = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service.generator..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.redhat.migrationtoolkit.rhcl.controller..",
                    "com.redhat.migrationtoolkit.rhcl.client..",
                    "com.redhat.migrationtoolkit.rhcl.entity..");

    @ArchTest
    static final ArchRule contributors_doNotDependOnConversionService = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service.generator.contributor..")
            .should().dependOnClassesThat().areAssignableTo(ConversionService.class);

    // ── Exception rules ───────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule exception_doesNotDependOnController = noClasses()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.exception..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.redhat.migrationtoolkit.rhcl.controller..");

    @ArchTest
    static final ArchRule controllerAndService_mayDependOnException = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.exception..")
            .should().onlyBeAccessed()
            .byClassesThat().resideInAnyPackage(
                    "com.redhat.migrationtoolkit.rhcl.controller..",
                    "com.redhat.migrationtoolkit.rhcl.service..",
                    "com.redhat.migrationtoolkit.rhcl.exception..");

    // ── General code quality rules ────────────────────────────────────────────

    @ArchTest
    static final ArchRule noClassesShouldUseSystemOutPrintln = noClasses()
            .should().callMethod(System.class, "out")
            .orShould().callMethod(System.class, "err");

    @ArchTest
    static final ArchRule noClassesShouldUseJavaUtilLogging = noClasses()
            .should().dependOnClassesThat()
            .resideInAPackage("java.util.logging..");

    // ── Typed YAML migration scaffold (#262) ───────────────────────────────────
    //
    // Shrinking allowlist: remove each class when its typed-yaml phase lands
    // (k8s-gateway → istio → kuadrant → httproute). When empty, every generator
    // and conversion support class must build YAML via Fabric8 models or Kuadrant
    // manifest records only. ReadmeSupport is excluded — it emits Markdown, not manifests.

    private static final Set<String> FORMATTED_YAML_GENERATOR_ALLOWLIST = Set.of(
            "AnonymousContributor",
            "AnonymousSecretContributor",
            "ApiKeyAuthenticationContributor",
            "ApiKeyGenerator",
            "ApiKeySecretContributor",
            "ApiProductGenerator",
            "AppIdKeyAuthenticationContributor",
            "AppIdKeySecretContributor",
            "AuthCachingContributor",
            "AuthorizationPolicyGenerator",
            "ConfigMapGenerator",
            "ContentLimitsEnvoyFilterGenerator",
            "CorsOptionsContributor",
            "DefaultCredentialsSecretContributor",
            "DestinationRuleGenerator",
            "DnsPolicyGenerator",
            "EmptyAuthenticationContributor",
            "GatewayGenerator",
            "HttpRouteAnnotationsContributor",
            "HttpRouteBuilder",
            "IpCheckOpaContributor",
            "JwtAuthenticationContributor",
            "LoggingEnvoyFilterGenerator",
            "MaintenanceModeEnvoyFilterGenerator",
            "MappingRulesContributor",
            "Oauth2IntrospectionContributor",
            "RetryContributor",
            "RetryEnvoyFilterGenerator",
            "RoutingContributor",
            "ServiceEntryGenerator",
            "TelemetryGenerator",
            "TlsPolicyGenerator",
            "TokenIntrospectionSecretContributor",
            "UpstreamContributor",
            "UrlRewritingEnvoyFilterGenerator"
    );

    private static final Set<String> FORMATTED_YAML_CONVERSION_ALLOWLIST = Set.of(
            "HttpRouteSupport",
            "JwtClaimCheckSupport",
            "RateLimitSupport",
            "RoutingSupport",
            "UpstreamSupport"
    );

    @ArchTest
    static final ArchRule generators_yamlMigration_usesShrinkingAllowlist = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service.generator..")
            .should(onlyUseFormattedYamlWhenAllowlisted(FORMATTED_YAML_GENERATOR_ALLOWLIST));

    @ArchTest
    static final ArchRule conversion_yamlMigration_usesShrinkingAllowlist = classes()
            .that().resideInAPackage("com.redhat.migrationtoolkit.rhcl.service.conversion..")
            .and(notReadmeSupport())
            .should(onlyUseFormattedYamlWhenAllowlisted(FORMATTED_YAML_CONVERSION_ALLOWLIST));

    private static DescribedPredicate<JavaClass> notReadmeSupport() {
        return new DescribedPredicate<>("not ReadmeSupport") {
            @Override
            public boolean test(JavaClass input) {
                return !"ReadmeSupport".equals(input.getSimpleName());
            }
        };
    }

    private static ArchCondition<JavaClass> onlyUseFormattedYamlWhenAllowlisted(Set<String> allowlist) {
        return new ArchCondition<>("use String.formatted() only when listed in the typed-YAML allowlist") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean callsFormatted = item.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTarget().getOwner().isEquivalentTo(String.class)
                                && "formatted".equals(call.getName()));
                if (callsFormatted && !allowlist.contains(item.getSimpleName())) {
                    String message = item.getFullName()
                            + " uses String.formatted() but is not in the typed-YAML allowlist";
                    events.add(SimpleConditionEvent.violated(item, message));
                }
            }
        };
    }
}
