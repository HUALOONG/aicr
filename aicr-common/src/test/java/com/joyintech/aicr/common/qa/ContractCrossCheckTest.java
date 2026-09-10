package com.joyintech.aicr.common.qa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA 独立验收：跨模块契约扫描（红线四 / 全枚举 of() / 常量类 final）。
 *
 * <p><b>独立性</b>：不依赖工程师写的测试，直接扫描源码 imports 与源码结构。
 * 相比依赖 classpath 反查字节码，源码扫描在 CI 环境（Windows/JAR/IDE）都稳定。
 *
 * <p>覆盖契约：
 * <ol>
 *   <li>红线四：源码中无黑名单 import（Spring/Jakarta/MyBatis/SLF4J/Jackson/hutool）；</li>
 *   <li>所有 enum 都有静态 {@code of()} 方法，且 unknown 输入返回 null；</li>
 *   <li>所有 {@code constant} 类都是 {@code final} + 私有构造（不可实例化）。</li>
 * </ol>
 */
@DisplayName("QA 独立验收 - 契约扫描")
class ContractCrossCheckTest {

    /** 黑名单包前缀（编码规范 §6.2 红线四）。 */
    private static final List<String> BLACKLIST_PACKAGES = List.of(
            "org.springframework",
            "org.springframework.boot",
            "jakarta",
            "javax.servlet",
            "org.mybatis",
            "com.baomidou",
            "org.slf4j",
            "ch.qos.logback",
            "com.fasterxml.jackson",
            "cn.hutool"
    );

    /**
     * 从源码根目录定位 aicr-common/src/main/java 目录。
     * 优先用 working directory 相对路径，失败时依次尝试常见 Maven 路径。
     */
    private static Path resolveMainJavaRoot() throws IOException {
        String[] candidates = {
                "aicr-common/src/main/java",
                "../aicr-common/src/main/java",
                "src/main/java"
        };
        for (String candidate : candidates) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IOException("无法定位 aicr-common/src/main/java，尝试过："
                + String.join(", ", candidates));
    }

    @Test
    @DisplayName("红线四：源码无黑名单 import")
    void redlineFourNoBlacklistedImportInSource() throws IOException {
        Path root = resolveMainJavaRoot();
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFiles::add);
        }
        assertFalse(javaFiles.isEmpty(), "源码目录为空，说明定位错误：" + root);

        List<String> violations = new ArrayList<>();
        Pattern importPattern = Pattern.compile("import\\s+([\\w\\.]+);");

        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                Matcher m = importPattern.matcher(line);
                if (m.find()) {
                    String fqcn = m.group(1);
                    for (String blacklist : BLACKLIST_PACKAGES) {
                        if (fqcn.startsWith(blacklist)) {
                            violations.add(String.format("%s → import %s",
                                    root.relativize(file), fqcn));
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "红线四被违反：以下文件引用了黑名单包\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("枚举数量：aicr-common 恰好 15 个枚举（源码扫描）")
    void enumCountInSource() throws IOException {
        Path root = resolveMainJavaRoot();
        Path enumsDir = root.resolve("com/joyintech/aicr/common/enums");
        assertTrue(Files.isDirectory(enumsDir), "enums 包路径不存在：" + enumsDir);

        List<String> enumFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(enumsDir)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    if (content.matches("(?s).*\\bpublic\\s+enum\\s+\\w+.*")) {
                        enumFiles.add(p.getFileName().toString());
                    }
                } catch (IOException ignored) {
                }
            });
        }
        assertEquals(15, enumFiles.size(),
                "aicr-common.enums 应有 15 个枚举，实际：" + enumFiles);
    }

    @Test
    @DisplayName("枚举 of() 存在性：所有 15 个枚举均有 public static of() 方法")
    void allEnumsHaveOfMethod() throws Exception {
        Class<?>[] enumClasses = new Class<?>[]{
                com.joyintech.aicr.common.enums.TaskSubStatus.class,
                com.joyintech.aicr.common.enums.TaskStatus.class,
                com.joyintech.aicr.common.enums.ErrorCode.class,
                com.joyintech.aicr.common.enums.RoleCode.class,
                com.joyintech.aicr.common.enums.PlatformType.class,
                com.joyintech.aicr.common.enums.MqEventType.class,
                com.joyintech.aicr.common.enums.EnableStatus.class,
                com.joyintech.aicr.common.enums.DataScope.class,
                com.joyintech.aicr.common.enums.ModelScene.class,
                com.joyintech.aicr.common.enums.NotifyChannel.class,
                com.joyintech.aicr.common.enums.TriggerCondition.class,
                com.joyintech.aicr.common.enums.ResourceType.class,
                com.joyintech.aicr.common.enums.PermSubjectType.class,
                com.joyintech.aicr.common.enums.MenuType.class,
                com.joyintech.aicr.common.enums.UserSource.class
        };

        assertEquals(15, enumClasses.length, "枚举类数量应为 15");

        for (Class<?> enumClass : enumClasses) {
            assertTrue(enumClass.isEnum(), enumClass.getName() + " 不是 enum");
            // 尝试 3 种签名：of(String)、of(int)、of(Integer)
            Method found = null;
            for (Class<?> paramType : new Class<?>[]{String.class, int.class, Integer.class}) {
                try {
                    found = enumClass.getMethod("of", paramType);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            assertNotNull(found, enumClass.getSimpleName() + " 缺少 of(String|int|Integer) 方法");
            assertTrue(Modifier.isStatic(found.getModifiers()),
                    enumClass.getSimpleName() + ".of() 应为 static");
            assertTrue(enumClass.isAssignableFrom(found.getReturnType()),
                    enumClass.getSimpleName() + ".of() 返回值应为 " + enumClass.getSimpleName());
        }
    }

    @Test
    @DisplayName("枚举 of() unknown 输入返回 null（不抛异常）")
    void allEnumsOfReturnsNullOnUnknown() {
        // 逐个枚举手工验证
        assertNull(com.joyintech.aicr.common.enums.TaskSubStatus.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.TaskSubStatus.of(null));
        assertNull(com.joyintech.aicr.common.enums.TaskStatus.of(9999));
        assertNull(com.joyintech.aicr.common.enums.TaskStatus.of(null));
        assertNull(com.joyintech.aicr.common.enums.ErrorCode.of(99999));
        assertNull(com.joyintech.aicr.common.enums.RoleCode.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.PlatformType.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.MqEventType.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.EnableStatus.of(9999));
        assertNull(com.joyintech.aicr.common.enums.DataScope.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.ModelScene.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.NotifyChannel.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.TriggerCondition.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.ResourceType.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.PermSubjectType.of("__NOT_EXIST__"));
        assertNull(com.joyintech.aicr.common.enums.MenuType.of(9999));
        assertNull(com.joyintech.aicr.common.enums.UserSource.of("__NOT_EXIST__"));
    }

    @Test
    @DisplayName("常量类均为 final + 私有构造（不可实例化）")
    void constantClassesAreFinalWithPrivateConstructor() throws Exception {
        Class<?>[] constantClasses = new Class<?>[]{
                com.joyintech.aicr.common.constant.KafkaConstant.class,
                com.joyintech.aicr.common.constant.PageConstant.class,
                com.joyintech.aicr.common.constant.RedisKeyConstant.class,
                com.joyintech.aicr.common.constant.TraceConstant.class
        };

        assertEquals(4, constantClasses.length, "constant 包应有 4 个类");

        for (Class<?> constantClass : constantClasses) {
            assertTrue(Modifier.isFinal(constantClass.getModifiers()),
                    constantClass.getSimpleName() + " 应为 final");

            Constructor<?>[] ctors = constantClass.getDeclaredConstructors();
            assertEquals(1, ctors.length,
                    constantClass.getSimpleName() + " 应只有一个构造函数");
            assertTrue(Modifier.isPrivate(ctors[0].getModifiers()),
                    constantClass.getSimpleName() + " 构造函数应为 private");
        }
    }

    @Test
    @DisplayName("constant 包内字段全部 static final（契约稳定）")
    void constantFieldsAreAllStaticFinal() throws IOException {
        Path root = resolveMainJavaRoot();
        Path constantDir = root.resolve("com/joyintech/aicr/common/constant");
        assertTrue(Files.isDirectory(constantDir));

        List<String> violations = new ArrayList<>();
        Pattern publicFieldPattern = Pattern.compile(
                "public\\s+(?!static|final)(?!static\\s+final).*\\b(\\w+)\\s*;");

        try (Stream<Path> walk = Files.walk(constantDir)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    // 简单检查：每个 public 字段声明应含 "static final"
                    for (String line : content.split("\n")) {
                        String trimmed = line.trim();
                        // 匹配形如 "public static final <TYPE> <NAME>"
                        if (trimmed.startsWith("public ") && trimmed.endsWith(";")) {
                            if (!trimmed.contains("static") || !trimmed.contains("final")) {
                                violations.add(file.getFileName() + ": " + trimmed);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "常量类中存在非 static final 的 public 字段：\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("枚举 Javadoc 有禁用 ordinal() 的注释（TaskSubStatus 契约）")
    void taskSubStatusJavadocWarnsOrdinal() throws IOException {
        Path root = resolveMainJavaRoot();
        Path taskSubStatusFile = root.resolve("com/joyintech/aicr/common/enums/TaskSubStatus.java");
        assertTrue(Files.exists(taskSubStatusFile));
        String content = Files.readString(taskSubStatusFile);
        // 检查是否包含对 ordinal() 的警告（防止持久化时误用）
        assertTrue(content.contains("ordinal"),
                "TaskSubStatus Javadoc 应警告禁用 ordinal()");
        assertTrue(content.contains("name()"),
                "TaskSubStatus Javadoc 应引导使用 name()");
    }

    @Test
    @DisplayName("关键契约常量值校验：RedisKeyConstant.CFG_REPO_MQ_TOPIC 与设计文档一致")
    void redisKeyConstantMatchesDesign() {
        assertEquals("aicr:cfg:repo:mqltopic:%s",
                com.joyintech.aicr.common.constant.RedisKeyConstant.CFG_REPO_MQ_TOPIC,
                "设计说明书 §3.4.4 / §4.7 锁定 key 名为 aicr:cfg:repo:mqltopic:%s");
        assertEquals(300L,
                com.joyintech.aicr.common.constant.RedisKeyConstant.TTL_CFG_REPO_MQ_TOPIC_SECONDS,
                "配置缓存 TTL 应为 300 秒");
    }

    @Test
    @DisplayName("PageConstant 边界值与编码规范 §3 一致")
    void pageConstantMatchesSpec() {
        assertEquals(1, com.joyintech.aicr.common.constant.PageConstant.MIN_PAGE);
        assertEquals(1, com.joyintech.aicr.common.constant.PageConstant.DEFAULT_PAGE);
        assertEquals(1, com.joyintech.aicr.common.constant.PageConstant.MIN_SIZE);
        assertEquals(20, com.joyintech.aicr.common.constant.PageConstant.DEFAULT_SIZE);
        assertEquals(100, com.joyintech.aicr.common.constant.PageConstant.MAX_SIZE);
    }

    @Test
    @DisplayName("TraceConstant HEADER_TRACE_ID 与 LLD §8.3 一致")
    void traceConstantHeaderName() {
        assertEquals("X-Trace-Id",
                com.joyintech.aicr.common.constant.TraceConstant.HEADER_TRACE_ID);
    }

    @Test
    @DisplayName("KafkaConstant：DEFAULT_TOPIC_BASENAME 与裁决 #12 一致")
    void kafkaConstantDefaults() {
        assertEquals("aicr.default",
                com.joyintech.aicr.common.constant.KafkaConstant.DEFAULT_TOPIC_BASENAME);
        assertEquals(".request",
                com.joyintech.aicr.common.constant.KafkaConstant.SUFFIX_REVIEW_REQUEST);
        assertEquals(".retry",
                com.joyintech.aicr.common.constant.KafkaConstant.SUFFIX_REVIEW_RETRY);
        assertEquals(".writeback",
                com.joyintech.aicr.common.constant.KafkaConstant.SUFFIX_WRITEBACK);
    }
}
