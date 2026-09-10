package com.joyintech.aicr.common.exception;

import com.joyintech.aicr.common.enums.ErrorCode;

/**
 * 零框架异常基类：只承载 {@code code + message}，不依赖任何容器或日志门面。
 *
 * <p>契约出处：LLD §8.1（异常 → 错误码映射）、编码规范（后端 Java）§5.3。
 *
 * <p>使用方：aicr-base 的 {@code BizException} 继承本类（裁决 #1，原名 BusinessException
 * 已改名为 BizException）；aicr-engine / aicr-webhook 如需自定义异常，应继承 aicr-base 的
 * {@code BizException}，<b>不直接继承本类</b>，以免出现 GlobalExceptionHandler 捕获不到的分支。
 *
 * <p>异常 → 错误码映射（LLD §8.1，裁决后口径）：
 * <ul>
 *   <li>{@code BizException} → 固定 40001；</li>
 *   <li>{@code BaseException} 其它子类 → {@code e.getCode()}（各自 {@link ErrorCode}，如 50002）；</li>
 *   <li>未捕获异常 → 50001。</li>
 * </ul>
 * handler 的 {@code @ExceptionHandler} 顺序不可颠倒（BizException → BaseException → Exception）。
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 使用错误码默认文案。 */
    protected BaseException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    /** 覆盖错误码默认文案。 */
    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 覆盖文案并保留原始堆栈。 */
    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** 错误码枚举（语义化）。 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 错误码数值（= {@code errorCode.code()}），供 GlobalExceptionHandler 直接写入 {@code R.code}。 */
    public int getCode() {
        return errorCode.code();
    }
}
