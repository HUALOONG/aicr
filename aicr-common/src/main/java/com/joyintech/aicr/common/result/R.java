package com.joyintech.aicr.common.result;

import com.joyintech.aicr.common.enums.ErrorCode;

import java.util.Map;

/**
 * 统一 RPC 响应体 {@code {code, message, data, requestId}}。
 *
 * <p>契约出处：openapi {@code RpcEnvelopeResponse} / LLD §5.1 / 编码规范 §3。
 * HTTP 状态码恒为 200，业务错误由 {@code code} 表达
 * （唯一例外：{@code POST /webhook/receive} 用 200/400/401/503，由 webhook Controller 控制，
 * 本结构不变）。
 *
 * <p>使用方：aicr-base（GlobalExceptionHandler 输出类型）、aicr-web、aicr-webhook。
 *
 * <p><b>不可变</b>：{@code final} 字段 + 私有构造 + 静态工厂；补全 {@code requestId}
 * 走 {@link #withRequestId} 返回新实例，不提供 setter。
 *
 * <p><b>不加任何 Jackson / Spring 注解</b>（裁决 #10）：{@code data} 为 null 时是否输出
 * {@code "data":null} 由 aicr-base 全局 ObjectMapper 的 {@code non_null} 策略统一决定。
 *
 * @param <T> 业务数据类型
 */
public final class R<T> {

    /** 0=成功；40001/40101/40301/40401/50001/50002（见 {@link ErrorCode}）。 */
    private final int code;

    /** 提示信息；参数校验失败时为概览文案，字段级明细置于 {@code data.detail}。 */
    private final String message;

    /** 业务数据；可为 {@code null}（写操作成功时）。 */
    private final T data;

    /** 与请求一致，缺省由服务端补全（编码规范 §7.1）。 */
    private final String requestId;

    private R(int code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    // ── 成功 ────────────────────────────────────────────────────

    /** 空成功：{@code code=0, message="success", data=null, requestId=null}。 */
    public static <T> R<T> ok() {
        return ok(null, null);
    }

    /** 携带 requestId 的空成功（data 由上层补全）。 */
    public static <T> R<T> ok(String requestId) {
        return ok(null, requestId);
    }

    /**
     * 携带业务数据的成功（requestId 由上层补全）。
     *
     * <p><b>重载消歧警告</b>：当 {@code T = String} 时，本方法与
     * {@link #ok(String)} 签名重合，javac 会按「最具体者优先」绑定到
     * {@link #ok(String)}（即被当作 requestId），导致 {@code data} 为 null。
     * 调用方需传 String 业务数据时，请将实参声明为 {@code Object}（或调用
     * {@link #ok(Object, String)}），不要写 {@code R.<String>ok("...")} ——
     * 显式类型见证无法排除该重载。
     */
    public static <T> R<T> ok(T data) {
        return ok(data, null);
    }

    /** 携带业务数据与 requestId 的成功。 */
    public static <T> R<T> ok(T data, String requestId) {
        return new R<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data, requestId);
    }

    // ── 失败 ────────────────────────────────────────────────────

    /** 使用错误码默认文案与空 data 的失败。 */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode, null);
    }

    /** 使用错误码默认文案，携带 requestId。 */
    public static <T> R<T> fail(ErrorCode errorCode, String requestId) {
        return new R<>(errorCode.code(), errorCode.message(), null, requestId);
    }

    /** 覆盖错误码默认文案（如把「参数校验失败」改为更具体的业务提示）。 */
    public static <T> R<T> fail(ErrorCode errorCode, String message, String requestId) {
        return new R<>(errorCode.code(), message, null, requestId);
    }

    /**
     * 字段级明细失败：{@code data} 直接携带 {@code {字段名: 错误提示}} 映射。
     *
     * <p>对应编码规范 §5.2（40001 时的字段级错误明细）；与 aicr-base 的
     * {@code BizException.detail} 一一对应。
     *
     * <p>返回类型固定为 {@code R<Map<String, String>>}（而非 {@code <T> R<T>}）：
     * {@code detail} 的静态类型已是 {@code Map<String, String>}，用泛型 T 会在
     * return 语句处因「等式约束 T / 下限 Map<String,String>」无法推断而编译失败。
     */
    public static R<Map<String, String>> fail(ErrorCode errorCode, Map<String, String> detail, String requestId) {
        return new R<>(errorCode.code(), errorCode.message(), detail, requestId);
    }

    /** 兜底：直接用原始码值构造（不推荐，绕过 {@link ErrorCode} 封闭枚举）。 */
    public static <T> R<T> fail(int code, String message, String requestId) {
        return new R<>(code, message, null, requestId);
    }

    // ── 读取 ────────────────────────────────────────────────────

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }

    /** 是否成功（{@code code == 0}）。 */
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.code();
    }

    /**
     * 用给定 requestId 复制一份（供 aicr-base 在入口补全 requestId 后回填）。
     *
     * @return 新实例（本对象不变）
     */
    public R<T> withRequestId(String requestId) {
        return new R<>(this.code, this.message, this.data, requestId);
    }
}
