package com.joyintech.aicr;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

/**
 * 架构约束固化（HLD §2.4.5 / LLD 附录 C.2）：在 mvn verify 阶段校验 HLD §2.4.4 依赖矩阵。
 * 任一违反将导致构建失败。扫描 classpath 上全部 9 个模块（worker 测试期依赖 web/webhook）。
 */
@AnalyzeClasses(
        packages = "com.joyintech.aicr",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    /** 校验模块依赖矩阵：单向无环，底层不反向依赖上层（红线一~三）。 */
    @ArchTest
    static final ArchRule moduleDependencyMatrix = Architectures.layeredArchitecture()
            .consideringAllDependencies()
            .withOptionalLayers(true)
            .layer("common").definedBy("com.joyintech.aicr.common..")
            .layer("api").definedBy("com.joyintech.aicr.api..")
            .layer("base").definedBy("com.joyintech.aicr.base..")
            .layer("security").definedBy("com.joyintech.aicr.security..")
            .layer("service").definedBy("com.joyintech.aicr.service..")
            .layer("engine").definedBy("com.joyintech.aicr.engine..")
            .layer("web").definedBy("com.joyintech.aicr.web..")
            .layer("webhook").definedBy("com.joyintech.aicr.webhook..")
            .layer("worker").definedBy("com.joyintech.aicr.worker..")
            // common 零框架依赖，任何模块可依赖它，它不得依赖任何业务模块（红线三）
            .whereLayer("common").mayOnlyBeAccessedByLayers("api", "base", "security", "service", "engine", "web", "webhook", "worker")
            .whereLayer("api").mayOnlyBeAccessedByLayers("base", "security", "service", "engine", "web", "webhook", "worker")
            .whereLayer("base").mayOnlyBeAccessedByLayers("security", "service", "engine", "web", "webhook", "worker")
            .whereLayer("security").mayOnlyBeAccessedByLayers("service", "engine", "web", "webhook", "worker")
            // service 仅能被 web / worker 依赖（engine 禁止依赖 service —— 红线一）
            .whereLayer("service").mayOnlyBeAccessedByLayers("web", "worker")
            // engine 唯一执行入口是 worker（红线一）
            .whereLayer("engine").mayOnlyBeAccessedByLayers("worker")
            // 装配层不被任何模块依赖
            .whereLayer("web").mayNotBeAccessedByAnyLayer()
            .whereLayer("webhook").mayNotBeAccessedByAnyLayer()
            .whereLayer("worker").mayNotBeAccessedByAnyLayer();

    /** 红线四：common 禁止依赖 Spring 与任何业务模块。 */
    @ArchTest
    static final ArchRule commonNoSpring = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.joyintech.aicr.common..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.springframework.boot..")
            .allowEmptyShould(true);
}
