package com.joyintech.aicr.common.result;

import com.joyintech.aicr.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link R} 契约测试。
 *
 * <p>覆盖：ok 家族 / fail 家族 / 不可变性 / 7 个错误码逐一断言。
 */
@DisplayName("R 统一响应体")
class RTest {

    @Nested
    @DisplayName("成功路径")
    class Ok {

        @Test
        @DisplayName("ok() 返回 code=0、message=success、data=null、requestId=null")
        void ok_empty() {
            R<Void> r = R.ok();
            assertEquals(0, r.getCode());
            assertEquals("success", r.getMessage());
            assertNull(r.getData());
            assertNull(r.getRequestId());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("ok(requestId) 携带 requestId 且 data 仍为 null")
        void ok_requestIdOnly() {
            R<Void> r = R.ok("req-001");
            assertEquals(0, r.getCode());
            assertNull(r.getData());
            assertEquals("req-001", r.getRequestId());
        }

        @Test
        @DisplayName("ok(data) 携带业务数据（非 String 载荷）")
        void ok_data() {
            Integer payload = 20260910;
            R<Integer> r = R.ok(payload);
            assertEquals(0, r.getCode());
            assertSame(payload, r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("ok(data) 携带 String 载荷（以 Object 声明避免与 ok(String requestId) 撞车）")
        void ok_dataStringPayload() {
            Object payload = "hello";
            R<Object> r = R.ok(payload);
            assertEquals(0, r.getCode());
            assertSame(payload, r.getData());
            assertNull(r.getRequestId());
        }

        @Test
        @DisplayName("重载消歧边界：ok(String) 被解析为 requestId 而非 data")
        void ok_stringOverloadResolvesToRequestId() {
            // javac 优先选最具体的 ok(String requestId)，故 data 为 null。
            // 这是设计文档 §4.1 给定的 API 形态，调用方传 String 业务数据须用显式类型见证。
            R<Void> r = R.ok("req-only");
            assertNull(r.getData());
            assertEquals("req-only", r.getRequestId());
        }

        @Test
        @DisplayName("ok(data, requestId) 同时携带数据与 requestId")
        void ok_dataAndRequestId() {
            R<Integer> r = R.ok(42, "req-002");
            assertEquals(0, r.getCode());
            assertEquals(Integer.valueOf(42), r.getData());
            assertEquals("req-002", r.getRequestId());
        }
    }

    @Nested
    @DisplayName("失败路径")
    class Fail {

        @Test
        @DisplayName("fail(ErrorCode) 使用错误码默认文案，data=null")
        void fail_errorCode() {
            R<Void> r = R.fail(ErrorCode.BAD_REQUEST);
            assertEquals(40001, r.getCode());
            assertEquals("参数校验失败", r.getMessage());
            assertNull(r.getData());
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("fail(ErrorCode, requestId) 携带 requestId")
        void fail_errorCodeRequestId() {
            R<Void> r = R.fail(ErrorCode.UNAUTHORIZED, "req-003");
            assertEquals(40101, r.getCode());
            assertEquals("未登录或Token过期", r.getMessage());
            assertEquals("req-003", r.getRequestId());
        }

        @Test
        @DisplayName("fail(ErrorCode, message, requestId) 覆盖默认文案")
        void fail_overrideMessage() {
            R<Void> r = R.fail(ErrorCode.BAD_REQUEST, "仓库未登记", "req-004");
            assertEquals(40001, r.getCode());
            assertEquals("仓库未登记", r.getMessage());
        }

        @Test
        @DisplayName("fail(ErrorCode, detail, requestId) 字段级明细落在 data 上")
        void fail_detail() {
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("repoName", "不能为空");
            detail.put("platformConfigId", "平台不存在");

            R<Map<String, String>> r = R.fail(ErrorCode.BAD_REQUEST, detail, "req-005");
            assertEquals(40001, r.getCode());
            assertSame(detail, r.getData());
            assertEquals(2, r.getData().size());
            assertEquals("不能为空", r.getData().get("repoName"));
        }

        @Test
        @DisplayName("fail(code, message, requestId) 兜底构造（原始码值）")
        void fail_rawCode() {
            R<Void> r = R.fail(50002, "LLM调用失败", "req-006");
            assertEquals(50002, r.getCode());
            assertEquals("LLM调用失败", r.getMessage());
            assertFalse(r.isSuccess());
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("withRequestId 返回新实例，原实例 requestId 不变")
        void withRequestId_returnsNewInstance() {
            R<Void> original = R.ok("req-old");
            R<Void> updated = original.withRequestId("req-new");

            assertNotSame(original, updated);
            assertEquals("req-old", original.getRequestId(), "原实例不应被修改");
            assertEquals("req-new", updated.getRequestId());
        }

        @Test
        @DisplayName("withRequestId 完整保留 code / message / data")
        void withRequestId_preservesAllFields() {
            R<String> original = R.ok("payload", "req-old");
            R<String> updated = original.withRequestId("req-new");

            assertNotSame(original, updated);
            assertEquals(original.getCode(), updated.getCode());
            assertEquals(original.getMessage(), updated.getMessage());
            assertSame(original.getData(), updated.getData());
            assertEquals("req-new", updated.getRequestId());
        }

        @Test
        @DisplayName("失败响应同样支持 withRequestId 回填")
        void fail_withRequestId() {
            R<Void> original = R.fail(ErrorCode.INTERNAL_ERROR, "req-old");
            R<Void> updated = original.withRequestId("req-new");

            assertNotSame(original, updated);
            assertEquals(50001, updated.getCode());
            assertEquals("req-new", updated.getRequestId());
            assertNull(updated.getData());
        }
    }

    @Nested
    @DisplayName("7 个错误码逐一断言（LLD §5.1 / 编码规范 §5.1 / openapi）")
    class ErrorCodeContract {

        @Test
        void success() {
            assertEquals(0, ErrorCode.SUCCESS.code());
            assertEquals("success", ErrorCode.SUCCESS.message());
            assertFalse(ErrorCode.SUCCESS.retryable());
            assertTrue(ErrorCode.isSuccess(0));
            assertSame(ErrorCode.SUCCESS, ErrorCode.of(0));
        }

        @Test
        void badRequest() {
            assertEquals(40001, ErrorCode.BAD_REQUEST.code());
            assertEquals("参数校验失败", ErrorCode.BAD_REQUEST.message());
            assertFalse(ErrorCode.BAD_REQUEST.retryable());
            assertSame(ErrorCode.BAD_REQUEST, ErrorCode.of(40001));
        }

        @Test
        void unauthorized() {
            assertEquals(40101, ErrorCode.UNAUTHORIZED.code());
            assertEquals("未登录或Token过期", ErrorCode.UNAUTHORIZED.message());
            assertFalse(ErrorCode.UNAUTHORIZED.retryable());
            assertSame(ErrorCode.UNAUTHORIZED, ErrorCode.of(40101));
        }

        @Test
        void forbidden() {
            assertEquals(40301, ErrorCode.FORBIDDEN.code());
            assertEquals("无权限", ErrorCode.FORBIDDEN.message());
            assertFalse(ErrorCode.FORBIDDEN.retryable());
            assertSame(ErrorCode.FORBIDDEN, ErrorCode.of(40301));
        }

        @Test
        void notFound() {
            assertEquals(40401, ErrorCode.NOT_FOUND.code());
            assertEquals("资源不存在", ErrorCode.NOT_FOUND.message());
            assertFalse(ErrorCode.NOT_FOUND.retryable());
            assertSame(ErrorCode.NOT_FOUND, ErrorCode.of(40401));
        }

        @Test
        void internalError() {
            assertEquals(50001, ErrorCode.INTERNAL_ERROR.code());
            assertEquals("系统内部错误", ErrorCode.INTERNAL_ERROR.message());
            assertTrue(ErrorCode.INTERNAL_ERROR.retryable());
            assertSame(ErrorCode.INTERNAL_ERROR, ErrorCode.of(50001));
        }

        @Test
        void llmError() {
            assertEquals(50002, ErrorCode.LLM_ERROR.code());
            assertEquals("LLM调用失败", ErrorCode.LLM_ERROR.message());
            assertTrue(ErrorCode.LLM_ERROR.retryable());
            assertSame(ErrorCode.LLM_ERROR, ErrorCode.of(50002));
        }

        @Test
        @DisplayName("封闭枚举：恰好 7 个值，无 OTHER 逃生口")
        void closedEnumExactlySeven() {
            assertEquals(7, ErrorCode.values().length);
        }

        @Test
        @DisplayName("of() 未知码返回 null 不抛异常")
        void of_unknownReturnsNull() {
            assertNull(ErrorCode.of(99999));
            assertNull(ErrorCode.of(-1));
        }
    }
}
