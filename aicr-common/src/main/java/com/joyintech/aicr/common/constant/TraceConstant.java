package com.joyintech.aicr.common.constant;

/**
 * 链路标识常量。
 *
 * <p>契约出处：LLD §8.3（链路追踪）、编码规范 §7.1、LLD §8.2（日志字段）。
 * <p>使用方：aicr-base（trace）、aicr-web、aicr-webhook、aicr-worker。
 *
 * <p>注意：MDC 的读写由 aicr-base 的 {@code TraceContext} 负责（准出判据 B6），
 * 本类只承载「头名」与「MDC key 字面量」，不含任何 SLF4J 依赖。
 */
public final class TraceConstant {

    /** HTTP 头名：Webhook 入口生成 traceId 后透传下游（LLD §8.3）。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** MDC key：traceId（与 LLD §8.2 日志字段名一致，待裁决 #4 已锁定）。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** MDC key：requestId（客户端生成，服务端入口补全，编码规范 §7.1）。 */
    public static final String MDC_REQUEST_ID = "requestId";

    /** MDC key：userId（登录用户标识，LLD §8.2）。 */
    public static final String MDC_USER_ID = "userId";

    private TraceConstant() {
    }
}
