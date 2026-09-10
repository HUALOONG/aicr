package com.joyintech.aicr.common.enums;

/**
 * 全局错误码（唯一口径，共 7 个）。
 *
 * <p>契约出处：openapi {@code RpcEnvelopeResponse.code} / 编码规范 §5.1 / LLD §5.1。
 * 三份文档口径一致（0/40001/40101/40301/40401/50001/50002）。
 *
 * <p>使用方：aicr-base（GlobalExceptionHandler）、aicr-security（40101/40301）、
 * aicr-engine（50002）、aicr-api、aicr-web、aicr-webhook。
 *
 * <p><b>封闭枚举</b>：不提供 "OTHER" 逃生口，禁止自定义新码
 * （编码规范（后端 Java）§5.3）。确需新增须走 PR 同步 openapi + 编码规范 §5 + 前端 §12。
 *
 * <p>{@code of()} 未知返回 {@code null} 而非抛异常，避免掩盖真实错误
 * （编码规范 §6.3 兜底口径，适用于枚举解析场景）。
 */
public enum ErrorCode {

    /** 成功。 */
    SUCCESS(0, "success"),

    /** 参数校验失败（含 {@code @Valid} 失败与业务规则不满足）。 */
    BAD_REQUEST(40001, "参数校验失败"),

    /** 未登录或 Token 过期。 */
    UNAUTHORIZED(40101, "未登录或Token过期"),

    /** 已登录但无权限。 */
    FORBIDDEN(40301, "无权限"),

    /** 资源不存在。 */
    NOT_FOUND(40401, "资源不存在"),

    /** 系统内部错误（未捕获异常的兜底）。 */
    INTERNAL_ERROR(50001, "系统内部错误"),

    /** LLM 调用失败（由 aicr-engine 抛出，前端提供重试按钮）。 */
    LLM_ERROR(50002, "LLM调用失败"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 错误码数值。 */
    public int code() {
        return code;
    }

    /** 默认提示文案。 */
    public String message() {
        return message;
    }

    /**
     * 是否可重试（仅 50001/50002 提供用户可见重试，编码规范 §5.3）。
     *
     * <p>注：这是「用户可见重试」口径，与 {@code TaskSubStatus.isRetryable()}
     * （人工重试来源口径）是两回事，不要混用。
     */
    public boolean retryable() {
        return this == INTERNAL_ERROR || this == LLM_ERROR;
    }

    /**
     * 按数值解析。
     *
     * @return 匹配的枚举；未知或无匹配返回 {@code null}（不抛异常）
     */
    public static ErrorCode of(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    /** 判断给定码值是否表示成功（{@code code == 0}）。 */
    public static boolean isSuccess(int code) {
        return code == SUCCESS.code;
    }
}
