package com.joyintech.aicr.common.enums;

import com.joyintech.aicr.common.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ErrorCode} 与 {@link BaseException} 契约测试。
 *
 * <p>覆盖：7 个错误码的码值/文案/retryable 口径、封闭枚举、{@code of()} 兜底、
 * {@code isSuccess()}、异常基类的三重载与映射。
 */
@DisplayName("ErrorCode 与 BaseException")
class ErrorCodeTest {

    @Nested
    @DisplayName("7 个错误码")
    class Codes {

        @Test
        void success() {
            assertSame(ErrorCode.SUCCESS, ErrorCode.of(0));
            assertEquals(0, ErrorCode.SUCCESS.code());
            assertEquals("success", ErrorCode.SUCCESS.message());
        }

        @Test
        void badRequest() {
            assertSame(ErrorCode.BAD_REQUEST, ErrorCode.of(40001));
            assertEquals(40001, ErrorCode.BAD_REQUEST.code());
            assertEquals("参数校验失败", ErrorCode.BAD_REQUEST.message());
        }

        @Test
        void unauthorized() {
            assertSame(ErrorCode.UNAUTHORIZED, ErrorCode.of(40101));
            assertEquals(40101, ErrorCode.UNAUTHORIZED.code());
            assertEquals("未登录或Token过期", ErrorCode.UNAUTHORIZED.message());
        }

        @Test
        void forbidden() {
            assertSame(ErrorCode.FORBIDDEN, ErrorCode.of(40301));
            assertEquals(40301, ErrorCode.FORBIDDEN.code());
            assertEquals("无权限", ErrorCode.FORBIDDEN.message());
        }

        @Test
        void notFound() {
            assertSame(ErrorCode.NOT_FOUND, ErrorCode.of(40401));
            assertEquals(40401, ErrorCode.NOT_FOUND.code());
            assertEquals("资源不存在", ErrorCode.NOT_FOUND.message());
        }

        @Test
        void internalError() {
            assertSame(ErrorCode.INTERNAL_ERROR, ErrorCode.of(50001));
            assertEquals(50001, ErrorCode.INTERNAL_ERROR.code());
            assertEquals("系统内部错误", ErrorCode.INTERNAL_ERROR.message());
        }

        @Test
        void llmError() {
            assertSame(ErrorCode.LLM_ERROR, ErrorCode.of(50002));
            assertEquals(50002, ErrorCode.LLM_ERROR.code());
            assertEquals("LLM调用失败", ErrorCode.LLM_ERROR.message());
        }

        @Test
        @DisplayName("封闭枚举：恰好 7 个值，无 OTHER 逃生口（编码规范 §5.3）")
        void closedEnum() {
            assertEquals(7, ErrorCode.values().length);
            for (ErrorCode value : ErrorCode.values()) {
                assertFalse(value.name().startsWith("OTHER"));
            }
        }
    }

    @Nested
    @DisplayName("retryable() 用户可见重试口径")
    class Retryable {

        @Test
        @DisplayName("仅 INTERNAL_ERROR / LLM_ERROR 可重试（编码规范 §5.3）")
        void onlyServerSideErrorsRetryable() {
            assertTrue(ErrorCode.INTERNAL_ERROR.retryable());
            assertTrue(ErrorCode.LLM_ERROR.retryable());

            assertFalse(ErrorCode.SUCCESS.retryable());
            assertFalse(ErrorCode.BAD_REQUEST.retryable());
            assertFalse(ErrorCode.UNAUTHORIZED.retryable());
            assertFalse(ErrorCode.FORBIDDEN.retryable());
            assertFalse(ErrorCode.NOT_FOUND.retryable());
        }

        @Test
        @DisplayName("retryable() 恰好 2 个错误码可重试")
        void exactlyTwoRetryable() {
            int count = 0;
            for (ErrorCode code : ErrorCode.values()) {
                if (code.retryable()) {
                    count++;
                }
            }
            assertEquals(2, count);
        }

        @Test
        @DisplayName("retryable() 是「用户可见重试」口径，与 TaskSubStatus.isRetryable() 独立")
        void retryable_isIndependentFromTaskSubStatus() {
            // FAILED 在 TaskSubStatus 侧可重试（人工重试来源），
            // 在 ErrorCode 侧 BAD_REQUEST.retryable() 为 false（不可重试错误码）—— 两个口径互不影响。
            assertTrue(TaskSubStatus.FAILED.isRetryable());
            assertFalse(ErrorCode.BAD_REQUEST.retryable());
        }
    }

    @Nested
    @DisplayName("of() 与 isSuccess()")
    class Resolution {

        @Test
        @DisplayName("isSuccess() 仅 0 为 true")
        void isSuccess_onlyZero() {
            assertTrue(ErrorCode.isSuccess(0));
            assertFalse(ErrorCode.isSuccess(40001));
            assertFalse(ErrorCode.isSuccess(50001));
            assertFalse(ErrorCode.isSuccess(-1));
            assertFalse(ErrorCode.isSuccess(99999));
        }

        @Test
        @DisplayName("of() 未知码返回 null 而非抛异常")
        void of_unknownReturnsNull() {
            assertNull(ErrorCode.of(99999));
            assertNull(ErrorCode.of(1));
            assertNull(ErrorCode.of(-1));
        }

        @Test
        @DisplayName("of() 全部 7 个合法码均可解析")
        void of_allCodesResolvable() {
            for (ErrorCode value : ErrorCode.values()) {
                assertSame(value, ErrorCode.of(value.code()),
                        value.name() + " 应可被 of() 解析");
            }
        }
    }

    @Nested
    @DisplayName("BaseException 异常基类")
    class BaseExceptionContract {

        /** 测试用具体子类（BaseException 为 abstract，无法直接实例化）。 */
        private static final class TestException extends BaseException {
            TestException(ErrorCode code) {
                super(code);
            }

            TestException(ErrorCode code, String message) {
                super(code, message);
            }

            TestException(ErrorCode code, String message, Throwable cause) {
                super(code, message, cause);
            }
        }

        @Test
        @DisplayName("BaseException 为 abstract（裁决 #1：common 只保留抽象基类）")
        void baseExceptionIsAbstract() {
            assertTrue(java.lang.reflect.Modifier.isAbstract(BaseException.class.getModifiers()));
        }

        @Test
        @DisplayName("三重载构造：单参使用错误码默认文案")
        void constructor_singleArg() {
            TestException ex = new TestException(ErrorCode.NOT_FOUND);
            assertEquals(40401, ex.getCode());
            assertSame(ErrorCode.NOT_FOUND, ex.getErrorCode());
            assertEquals("资源不存在", ex.getMessage());
        }

        @Test
        @DisplayName("两参构造：覆盖默认文案")
        void constructor_twoArg() {
            TestException ex = new TestException(ErrorCode.BAD_REQUEST, "仓库未登记");
            assertEquals(40001, ex.getCode());
            assertEquals("仓库未登记", ex.getMessage());
        }

        @Test
        @DisplayName("三参构造：保留原始堆栈")
        void constructor_threeArg() {
            Throwable cause = new IllegalStateException("boom");
            TestException ex = new TestException(ErrorCode.LLM_ERROR, "LLM 超时", cause);
            assertEquals(50002, ex.getCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("getCode() 与 getErrorCode().code() 一致")
        void getCode_consistentWithErrorCode() {
            for (ErrorCode code : ErrorCode.values()) {
                TestException ex = new TestException(code);
                assertEquals(code.code(), ex.getCode(), code.name() + " 的映射应一致");
            }
        }

        @Test
        @DisplayName("继承 RuntimeException（可被 GlobalExceptionHandler 统一捕获）")
        void extendsRuntimeException() {
            assertNotNull(new TestException(ErrorCode.INTERNAL_ERROR));
            assertTrue(new TestException(ErrorCode.INTERNAL_ERROR) instanceof RuntimeException);
        }
    }
}
