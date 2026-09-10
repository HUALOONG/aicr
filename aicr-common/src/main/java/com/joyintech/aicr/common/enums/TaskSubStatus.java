package com.joyintech.aicr.common.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务业务状态 {@code review_task.sub_status}（String），全局唯一口径，共 14 个。
 *
 * <p>契约出处：LLD §6.2 / 附录B（声明顺序依据）、openapi {@code SubStatus}、
 * 编码规范 §6.1、DBD §5.1。
 *
 * <p><b>命名映射</b>：openapi schema 名为 {@code SubStatus}，Java 类名为 {@code TaskSubStatus}
 * （裁决 #2 已锁定：Java 侧用 {@code TaskSubStatus} 以消歧「任务」域，openapi 保持 {@code SubStatus} 不动）。
 *
 * <p><b>⚠️ 硬性约定</b>：禁止使用 {@code ordinal()} 做持久化或传输，一律用 {@link #name()}
 * （与库列 {@code VARCHAR(32)} 对齐；裁决 #3 锁定声明顺序按 LLD 附录B 的主状态分组）。
 *
 * <p>使用方：aicr-webhook（建任务写初值 RECEIVED）、aicr-worker（消费前终态丢弃）、
 * aicr-engine（状态推进与审计）、aicr-service（列表筛选与 retry 校验）、aicr-api（DTO 字段类型）。
 */
public enum TaskSubStatus {

    // ── 主状态 0 待处理 ─────────────────────────────────────────

    /** 已接收（Webhook 落库后的初始态）。 */
    RECEIVED("已接收", TaskStatus.PENDING),

    /** 解析中（Diff 拉取与解析）。 */
    PARSING("解析中", TaskStatus.PENDING),

    /** 排队中（已入 Kafka，等待 worker 消费）。 */
    QUEUED("排队中", TaskStatus.PENDING),

    // ── 主状态 1 处理中 ─────────────────────────────────────────

    /** 分析中（LLM 评审）。 */
    ANALYZING("分析中", TaskStatus.PROCESSING),

    /** 测试生成中。 */
    GENERATING_TEST("测试生成中", TaskStatus.PROCESSING),

    /** 回写中（评审意见写回代码平台）。 */
    WRITING_BACK("回写中", TaskStatus.PROCESSING),

    /** 重试中（LLM 失败后自动重试）。 */
    RETRYING("重试中", TaskStatus.PROCESSING),

    /** 超时（LLM 调用超时）。 */
    TIMEOUT("超时", TaskStatus.PROCESSING),

    // ── 主状态 2 已完成 ─────────────────────────────────────────

    /** 已完成（全部成功）。 */
    COMPLETED("已完成", TaskStatus.COMPLETED),

    /** 部分成功（评审成功但测试生成失败等）。 */
    PARTIAL_SUCCESS("部分成功", TaskStatus.COMPLETED),

    /** 降级完成（走 FallbackMode 降级路径）。 */
    DEGRADED("降级完成", TaskStatus.COMPLETED),

    // ── 主状态 3 失败 ───────────────────────────────────────────

    /** 失败（重试耗尽或平台连接异常）。 */
    FAILED("失败", TaskStatus.FAILED),

    // ── 主状态 4 已取消 ─────────────────────────────────────────

    /** 已取消（用户主动取消或排队中超时取消）。 */
    CANCELLED("已取消", TaskStatus.CANCELLED),

    /** 已跳过（配额不足，未进入分析）。 */
    SKIPPED_QUOTA("已跳过（配额不足）", TaskStatus.CANCELLED),
    ;

    private final String label;
    private final TaskStatus mainStatus;

    /**
     * 静态流转判定表（LLD §6.2 的 24 条，含 §6.4 三条受控回退）。
     *
     * <p>终态（COMPLETED / FAILED / CANCELLED / SKIPPED_QUOTA）不出现在本表中，
     * 即 {@code canTransitionTo()} 对它们恒返回 {@code false}（终态不可再流转）。
     */
    private static final Map<TaskSubStatus, Set<TaskSubStatus>> TRANSITIONS = buildTransitions();

    TaskSubStatus(String label, TaskStatus mainStatus) {
        this.label = label;
        this.mainStatus = mainStatus;
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 主状态映射（LLD §6.3 的 5↔14 映射）。 */
    public TaskStatus mainStatus() {
        return mainStatus;
    }

    /**
     * 是否为终态（<b>MQ 丢弃口径</b>：COMPLETED / FAILED / CANCELLED，LLD §10.4）。
     *
     * <p>worker 消费前用本方法判定「该任务已终结，直接丢弃消息」；
     * 注意 {@code SKIPPED_QUOTA} 虽为主状态 CANCELLED，但按 LLD §10.4 的 MQ 丢弃口径不计入。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * 是否允许作为人工重试来源（<b>人工重试口径</b>：FAILED / TIMEOUT / DEGRADED / PARTIAL_SUCCESS，
     * openapi {@code ReviewRetryParam.retryFrom}）。
     *
     * <p>与 {@link #isTerminal()} 是两个独立口径（裁决 #7 已锁定）：
     * TIMEOUT / DEGRADED / PARTIAL_SUCCESS 不是终态但允许人工重试；
     * FAILED 既是终态也允许人工重试。
     */
    public boolean isRetryable() {
        return this == FAILED || this == TIMEOUT || this == DEGRADED || this == PARTIAL_SUCCESS;
    }

    /**
     * 静态流转判定：从当前状态能否合法流转到 {@code target}。
     *
     * <p>⚠️ 本方法<b>只做「是否合法」判定</b>，不含执行语义
     * （审计日志、重试次数累加、Kafka 联动在 aicr-engine 的 {@code TaskStateMachine}）。
     *
     * @param target 目标状态；{@code null} 返回 {@code false}
     */
    public boolean canTransitionTo(TaskSubStatus target) {
        if (target == null) {
            return false;
        }
        Set<TaskSubStatus> targets = TRANSITIONS.get(this);
        return targets != null && targets.contains(target);
    }

    /**
     * 合法后继集合（供 aicr-engine 状态机与前端「可操作」按钮态推导）。
     *
     * @return 不可变后继集合；终态返回空集合
     */
    public Set<TaskSubStatus> nextStatuses() {
        Set<TaskSubStatus> targets = TRANSITIONS.get(this);
        return (targets == null) ? EnumSet.noneOf(TaskSubStatus.class) : EnumSet.copyOf(targets);
    }

    /**
     * 按名称解析（对齐 VARCHAR 列存储值）。
     *
     * @param code 状态名（如 {@code "ANALYZING"}）
     * @return 匹配的枚举；未知或 {@code null} 返回 {@code null}（编码规范 §6.3 兜底，不抛异常）
     */
    public static TaskSubStatus of(String code) {
        if (code == null) {
            return null;
        }
        for (TaskSubStatus value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }

    /** 终态集合（MQ 丢弃口径：COMPLETED / FAILED / CANCELLED）。 */
    public static Set<TaskSubStatus> terminalStatuses() {
        return EnumSet.of(COMPLETED, FAILED, CANCELLED);
    }

    /** 可重试来源集合（人工重试口径：FAILED / TIMEOUT / DEGRADED / PARTIAL_SUCCESS）。 */
    public static Set<TaskSubStatus> retryableStatuses() {
        return EnumSet.of(FAILED, TIMEOUT, DEGRADED, PARTIAL_SUCCESS);
    }

    /**
     * 构建 24 条流转边（LLD §6.2 全表，逐条与设计说明书 §4.6 对齐）。
     *
     * <p>计数校验：2 + 2 + 2 + 6 + 2 + 3 + 3 + 2 + 1 + 1 = <b>24</b>。
     */
    private static Map<TaskSubStatus, Set<TaskSubStatus>> buildTransitions() {
        Map<TaskSubStatus, Set<TaskSubStatus>> table = new EnumMap<>(TaskSubStatus.class);

        // RECEIVED → PARSING, SKIPPED_QUOTA
        table.put(RECEIVED, EnumSet.of(PARSING, SKIPPED_QUOTA));
        // PARSING → QUEUED, FAILED
        table.put(PARSING, EnumSet.of(QUEUED, FAILED));
        // QUEUED → ANALYZING, CANCELLED
        table.put(QUEUED, EnumSet.of(ANALYZING, CANCELLED));
        // ANALYZING → GENERATING_TEST, WRITING_BACK, RETRYING, FAILED, DEGRADED, CANCELLED
        table.put(ANALYZING, EnumSet.of(
                GENERATING_TEST, WRITING_BACK, RETRYING, FAILED, DEGRADED, CANCELLED));
        // RETRYING → ANALYZING, FAILED
        table.put(RETRYING, EnumSet.of(ANALYZING, FAILED));
        // GENERATING_TEST → WRITING_BACK, PARTIAL_SUCCESS, CANCELLED
        table.put(GENERATING_TEST, EnumSet.of(WRITING_BACK, PARTIAL_SUCCESS, CANCELLED));
        // WRITING_BACK → COMPLETED, PARTIAL_SUCCESS, TIMEOUT
        table.put(WRITING_BACK, EnumSet.of(COMPLETED, PARTIAL_SUCCESS, TIMEOUT));
        // TIMEOUT → RETRYING, FAILED
        table.put(TIMEOUT, EnumSet.of(RETRYING, FAILED));
        // PARTIAL_SUCCESS → WRITING_BACK（受控回退之一）
        table.put(PARTIAL_SUCCESS, EnumSet.of(WRITING_BACK));
        // DEGRADED → ANALYZING（受控回退之一）
        table.put(DEGRADED, EnumSet.of(ANALYZING));

        // COMPLETED / FAILED / CANCELLED / SKIPPED_QUOTA 为终态，无后继，不写入表中。

        return Collections.unmodifiableMap(table);
    }
}
