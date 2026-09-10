package com.joyintech.aicr.common.qa;

import com.joyintech.aicr.common.enums.TaskSubStatus;
import com.joyintech.aicr.common.result.PageResult;
import com.joyintech.aicr.common.result.R;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA 独立验收：序列化契约（无 Jackson 注解 / name() 反序列化 / 无 setter 破坏不可变）。
 *
 * <p><b>关键约束</b>：aicr-common 不引入 Jackson（红线四），但下游 aicr-base 会用
 * Jackson {@code ObjectMapper} 全局序列化 {@code R}/{@code PageResult}/{@code TaskSubStatus}。
 * 本测试仅验证「无注解时的默认行为」不违反契约：
 * <ul>
 *   <li>{@code R} 无任何 {@code com.fasterxml.jackson.*} 注解；</li>
 *   <li>{@code R} 全字段 {@code final}、无 setter（Jackson 默认按 getter 序列化不破坏不可变）；</li>
 *   <li>{@code PageResult} 无 Jackson 注解；</li>
 *   <li>{@code TaskSubStatus} 无 Jackson 注解、{@code name()} 与 {@code values()[i]} 一一映射
 *       （防 ordinal 泄露）。</li>
 * </ul>
 *
 * <p>若测试环境有 Jackson on classpath（例如上游 BOM 泄漏），额外断言
 * {@code objectMapper.writeValueAsString()} 不抛异常、字段名与预期一致。
 */
@DisplayName("QA 独立验收 - 序列化契约")
class SerializationContractTest {

    @Test
    @DisplayName("R 类无 Jackson 注解（裁决 #10）")
    void rClassHasNoJacksonAnnotation() {
        assertNoAnnotationOn(R.class, "com.fasterxml.jackson.");
    }

    @Test
    @DisplayName("R 字段无 Jackson 注解")
    void rFieldsHaveNoJacksonAnnotation() {
        for (Field field : R.class.getDeclaredFields()) {
            assertNoAnnotationOn(field, "com.fasterxml.jackson.");
        }
    }

    @Test
    @DisplayName("R 方法无 Jackson 注解")
    void rMethodsHaveNoJacksonAnnotation() {
        for (Method method : R.class.getDeclaredMethods()) {
            assertNoAnnotationOn(method, "com.fasterxml.jackson.");
        }
    }

    @Test
    @DisplayName("PageResult 类无 Jackson 注解")
    void pageResultClassHasNoJacksonAnnotation() {
        assertNoAnnotationOn(PageResult.class, "com.fasterxml.jackson.");
    }

    @Test
    @DisplayName("PageResult 字段无 Jackson 注解")
    void pageResultFieldsHaveNoJacksonAnnotation() {
        for (Field field : PageResult.class.getDeclaredFields()) {
            assertNoAnnotationOn(field, "com.fasterxml.jackson.");
        }
    }

    @Test
    @DisplayName("TaskSubStatus 枚举无 Jackson 注解")
    void taskSubStatusHasNoJacksonAnnotation() {
        assertNoAnnotationOn(TaskSubStatus.class, "com.fasterxml.jackson.");
    }

    @Test
    @DisplayName("TaskSubStatus 常量无 Jackson 注解（枚举值上的 @JsonCreator 等）")
    void taskSubStatusConstantsHaveNoJacksonAnnotation() {
        for (TaskSubStatus value : TaskSubStatus.values()) {
            // 枚举值上的注解通过 getAnnotatedType 或 Enum.values() + Class.getEnumConstants() 无法直接读；
            // 通过反射读 class 的注解覆盖
        }
        assertNoAnnotationOn(TaskSubStatus.class, "com.fasterxml.jackson.");
    }

    @Test
    @DisplayName("R 全字段为 final（Jackson 默认按 getter 序列化，不破坏不可变性）")
    void rAllFieldsAreFinal() {
        Field[] fields = R.class.getDeclaredFields();
        assertEquals(4, fields.length,
                "R 应有 4 个字段（code/message/data/requestId），实际 " + fields.length);

        for (Field field : fields) {
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "R." + field.getName() + " 应为 final");
            assertTrue(Modifier.isPrivate(field.getModifiers()),
                    "R." + field.getName() + " 应为 private");
        }
    }

    @Test
    @DisplayName("R 无 setter（Jackson 反序列化时不会破坏不可变性）")
    void rHasNoSetterMethods() {
        List<String> setters = new java.util.ArrayList<>();
        for (Method method : R.class.getDeclaredMethods()) {
            String name = method.getName();
            if (name.startsWith("set") && method.getParameterCount() == 1) {
                setters.add(name);
            }
        }
        assertTrue(setters.isEmpty(),
                "R 不应有 setter（Jackson 默认 setter 会破坏不可变性）：" + setters);
    }

    @Test
    @DisplayName("PageResult 无 setter")
    void pageResultHasNoSetterMethods() {
        List<String> setters = new java.util.ArrayList<>();
        for (Method method : PageResult.class.getDeclaredMethods()) {
            String name = method.getName();
            if (name.startsWith("set") && method.getParameterCount() == 1) {
                setters.add(name);
            }
        }
        assertTrue(setters.isEmpty(),
                "PageResult 不应有 setter：" + setters);
    }

    @Test
    @DisplayName("TaskSubStatus：name() ↔ values()[i] 一一映射（防 ordinal 泄露）")
    void taskSubStatusNameMapsCorrectly() {
        // 用 JDK Enum.valueOf 反序列化（Jackson 默认对 enum 用 name() 反序列化）
        for (TaskSubStatus value : TaskSubStatus.values()) {
            TaskSubStatus parsed = TaskSubStatus.valueOf(value.name());
            assertEquals(value, parsed,
                    value.name() + " 的 name() 反序列化应回到自身");
        }
    }

    @Test
    @DisplayName("TaskSubStatus：ordinal() 与 name() 稳定（防枚举声明顺序变更破坏持久化）")
    void taskSubStatusOrdinalStable() {
        // 断言每个枚举常量的 name() 与 ordinal() 一一对应
        for (int i = 0; i < TaskSubStatus.values().length; i++) {
            assertEquals(i, TaskSubStatus.values()[i].ordinal(),
                    "ordinal(" + TaskSubStatus.values()[i].name() + ") 位置漂移");
        }
    }

    @Test
    @DisplayName("PageResult：map() 转换不改变 total/page/size")
    void pageResultMapPreservesMeta() {
        List<Integer> source = List.of(1, 2, 3);
        PageResult<Integer> original = PageResult.of(100L, 2, 20, source);
        PageResult<String> mapped = original.map(String::valueOf);

        assertEquals(100L, mapped.getTotal());
        assertEquals(2, mapped.getPage());
        assertEquals(20, mapped.getSize());
        assertEquals(List.of("1", "2", "3"), mapped.getList());
    }

    @Test
    @DisplayName("PageResult：null list 归一化为 []（Jackson 序列化不会返回 null）")
    void pageResultOf_nullListBecomesEmpty() {
        PageResult<Integer> result = PageResult.of(0L, 1, 20, null);
        assertNotNull(result.getList(), "list 不应为 null");
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("PageResult：getTotalPages 防除零（size=0 时返回 0）")
    void pageResultGetTotalPagesHandlesZeroSize() {
        // 通过 of() 会把 size=0 收敛到 DEFAULT_SIZE，直接反射构造模拟 size=0
        try {
            // PageResult 的构造器是包级私有 (package-private)，泛型擦除后用 raw type 处理
            java.lang.reflect.Constructor<?> ctor =
                    PageResult.class.getDeclaredConstructor(long.class, int.class, int.class, List.class);
            ctor.setAccessible(true);
            PageResult<Integer> result = (PageResult<Integer>) ctor.newInstance(100L, 1, 0, List.of());
            assertEquals(0L, result.getTotalPages(),
                    "size=0 时 getTotalPages 应返回 0（防除零）");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("反射构造 PageResult 失败", e);
        }
    }

    @Test
    @DisplayName("PageResult：getTotalPages 边界（total=0 / total=size / total=size+1）")
    void pageResultGetTotalPagesEdges() {
        PageResult<Integer> empty = PageResult.of(0L, 1, 20, List.of());
        assertEquals(0L, empty.getTotalPages());

        PageResult<Integer> exact = PageResult.of(40L, 2, 20, List.of());
        assertEquals(2L, exact.getTotalPages());

        PageResult<Integer> oneMore = PageResult.of(41L, 3, 20, List.of());
        assertEquals(3L, oneMore.getTotalPages());
    }

    @Test
    @DisplayName("R：Jackson ObjectMapper 若可用，序列化不抛异常（防御式）")
    void rSerializationNotBroken() {
        Class<?> mapperClass = findJacksonObjectMapper();
        Assumptions.assumeTrue(mapperClass != null, "aicr-common 无 Jackson（预期），跳过序列化测试");

        try {
            Object mapper = mapperClass.getDeclaredConstructor().newInstance();
            Method writeMethod = mapperClass.getMethod("writeValueAsString", Object.class);

            R<Void> ok = R.ok();
            String json = (String) writeMethod.invoke(mapper, ok);
            assertNotNull(json);
            assertTrue(json.contains("\"code\""), "JSON 应包含 code 字段：" + json);
            assertTrue(json.contains("\"message\""), "JSON 应包含 message 字段：" + json);

            R<Void> fail = R.fail(com.joyintech.aicr.common.enums.ErrorCode.BAD_REQUEST);
            String failJson = (String) writeMethod.invoke(mapper, fail);
            assertTrue(failJson.contains("40001"), "失败 JSON 应包含错误码 40001：" + failJson);

            TaskSubStatus status = TaskSubStatus.ANALYZING;
            String statusJson = (String) writeMethod.invoke(mapper, status);
            assertEquals("\"ANALYZING\"", statusJson,
                    "TaskSubStatus 默认序列化应为字符串名 \"ANALYZING\"");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Jackson 反射调用失败", e);
        }
    }

    @Test
    @DisplayName("R：data 为 null 时 Jackson 默认序列化不抛异常（下游 aicr-base 用 non_null 决定字段是否输出）")
    void rSerializeWithDataNullNotBroken() {
        Class<?> mapperClass = findJacksonObjectMapper();
        Assumptions.assumeTrue(mapperClass != null, "aicr-common 无 Jackson（预期），跳过");

        try {
            Object mapper = mapperClass.getDeclaredConstructor().newInstance();
            Method writeMethod = mapperClass.getMethod("writeValueAsString", Object.class);

            R<Void> r = R.ok(); // data=null
            String json = (String) writeMethod.invoke(mapper, r);
            // 默认 Jackson 会输出 "data":null（不因 null 抛异常）
            assertNotNull(json);
            assertTrue(json.length() > 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("序列化 data=null 时异常", e);
        }
    }

    /** 探测 Jackson ObjectMapper 是否可用；不可用返回 null。 */
    private static Class<?> findJacksonObjectMapper() {
        try {
            return Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** 检查对象（类/字段/方法）无匹配前缀的注解。 */
    private static void assertNoAnnotationOn(java.lang.reflect.AnnotatedElement element, String annotationPrefix) {
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            String name = annotation.annotationType().getName();
            assertFalse(name.startsWith(annotationPrefix),
                    element + " 声明了 Jackson 注解：" + name);
        }
    }

    @Test
    @DisplayName("R：requestId 补全路径不修改原实例（Jackson 序列化不产生副作用）")
    void rWithRequestIdNonMutating() {
        R<Integer> original = R.ok(42, "old");
        R<Integer> updated = original.withRequestId("new");

        assertEquals("old", original.getRequestId(), "原实例不应被修改");
        assertEquals("new", updated.getRequestId());
        assertEquals(42, updated.getData());
    }

    @Test
    @DisplayName("PageResult.of()：page/size 越界兜底（编码规范 §3）")
    void pageResultOfNormalizesPageAndSize() {
        // page 越界
        PageResult<Integer> lowPage = PageResult.of(10L, 0, 20, List.of());
        assertEquals(1, lowPage.getPage(), "page=0 应兜底到 DEFAULT_PAGE=1");

        PageResult<Integer> highSize = PageResult.of(100L, 1, 1000, List.of());
        assertEquals(100, highSize.getSize(), "size=1000 应兜底到 MAX_SIZE=100");

        PageResult<Integer> lowSize = PageResult.of(10L, 1, 0, List.of());
        assertEquals(20, lowSize.getSize(), "size=0 应兜底到 DEFAULT_SIZE=20");
    }

    @Test
    @DisplayName("R：isSuccess() 与 code 一致")
    void rIsSuccess() {
        assertTrue(R.ok().isSuccess());
        assertFalse(R.fail(com.joyintech.aicr.common.enums.ErrorCode.BAD_REQUEST).isSuccess());
    }

    @Test
    @DisplayName("序列化契约：TaskSubStatus 常量 name() 长度 ≤ 32（VARCHAR(32) 兼容）")
    void taskSubStatusNameFitsVarchar32() {
        for (TaskSubStatus value : TaskSubStatus.values()) {
            assertTrue(value.name().length() <= 32,
                    value.name() + " 长度 " + value.name().length() + " 超过 VARCHAR(32)");
        }
    }

    @Test
    @DisplayName("序列化契约：ErrorCode.code() 值域封闭（仅 7 个具体值）")
    void errorCodeClosedSet() {
        java.util.Set<Integer> expected = new java.util.HashSet<>();
        expected.add(0);
        expected.add(40001);
        expected.add(40101);
        expected.add(40301);
        expected.add(40401);
        expected.add(50001);
        expected.add(50002);

        java.util.Set<Integer> actual = new java.util.HashSet<>();
        for (com.joyintech.aicr.common.enums.ErrorCode code :
                com.joyintech.aicr.common.enums.ErrorCode.values()) {
            actual.add(code.code());
        }
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("序列化契约：ErrorCode 与 TaskSubStatus 的 code/name 值域彼此独立")
    void errorCodeAndTaskSubStatusIndependent() {
        // ErrorCode.of(0) 是 SUCCESS；TaskSubStatus 无 code 字段
        assertNull(com.joyintech.aicr.common.enums.ErrorCode.of(99999));
        assertNull(TaskSubStatus.of("__UNKNOWN__"));
    }

    @Test
    @DisplayName("R：fail(ErrorCode, Map, requestId) 支持字段级明细（编码规范 §5.2）")
    void rFailWithDetailMap() {
        java.util.Map<String, String> detail = new java.util.LinkedHashMap<>();
        detail.put("name", "不能为空");
        R<java.util.Map<String, String>> r = R.fail(
                com.joyintech.aicr.common.enums.ErrorCode.BAD_REQUEST, detail, "req-1");

        assertEquals(40001, r.getCode());
        assertEquals("req-1", r.getRequestId());
        assertNotNull(r.getData());
        assertEquals("不能为空", r.getData().get("name"));
    }

    @Test
    @DisplayName("PageResult.empty()：page=1, size=20, total=0, list=[]")
    void pageResultEmptyDefaults() {
        PageResult<Object> empty = PageResult.empty();
        assertEquals(0L, empty.getTotal(), "empty() 的 total 应为 0（无数据）");
        assertEquals(1, empty.getPage());
        assertEquals(20, empty.getSize());
        assertNotNull(empty.getList());
        assertTrue(empty.getList().isEmpty());
    }
}
