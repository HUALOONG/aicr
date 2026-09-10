package com.joyintech.aicr.common.enums;

/**
 * 通知触发条件。
 *
 * <p>契约出处：openapi {@code TriggerCondition}（enum: REVIEW_COMPLETED / BLOCKER_FOUND /
 * TASK_FAILED / QUOTA_WARNING）。
 * <p>使用方：aicr-service（通知配置管理，一个配置可绑定多个触发条件）、
 * aicr-worker（事件命中判定）、aicr-web（管理端）、aicr-api。
 */
public enum TriggerCondition {

    /** 评审完成时触发。 */
    REVIEW_COMPLETED("评审完成"),

    /** 发现阻断级问题时触发。 */
    BLOCKER_FOUND("发现阻断级问题"),

    /** 任务失败时触发。 */
    TASK_FAILED("任务失败"),

    /** 配额告警时触发（SRS FR-021）。 */
    QUOTA_WARNING("配额告警"),
    ;

    private final String label;

    TriggerCondition(String label) {
        this.label = label;
    }

    /** 条件码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 按条件码解析；未知或 {@code null} 返回 {@code null}。 */
    public static TriggerCondition of(String code) {
        if (code == null) {
            return null;
        }
        for (TriggerCondition value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
