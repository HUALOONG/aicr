package com.joyintech.aicr.common.enums;

/**
 * 任务主状态 {@code review_task.status}（int），仅用于统计聚合（编码规范 §6.1）。
 *
 * <p>契约出处：LLD §6.1 / DBD §5.1 / openapi {@code MainStatus}。
 * 单向不可逆：{@code 0 → 1 → 2/3/4}；禁止 2/3/4 互转、禁止回退 0。
 *
 * <p>使用方：aicr-service（统计聚合）、aicr-api（DTO 字段类型）、aicr-engine。
 * 列表展示与筛选一律用 {@link TaskSubStatus}（14 态），本枚举不参与筛选。
 *
 * <p>主状态流转实际由 {@link TaskSubStatus} 驱动，故本类不提供 {@code canTransitionTo()}
 * （设计说明书 §2.4 第 22 项标注为 P2、可不实现）。
 */
public enum TaskStatus {

    /** 待处理（0）。 */
    PENDING(0, "待处理"),

    /** 处理中（1）。 */
    PROCESSING(1, "处理中"),

    /** 已完成（2）。 */
    COMPLETED(2, "已完成"),

    /** 失败（3）。 */
    FAILED(3, "失败"),

    /** 已取消（4）。 */
    CANCELLED(4, "已取消"),
    ;

    private final int code;
    private final String label;

    TaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 状态数值（与 {@code review_task.status} 列对齐）。 */
    public int code() {
        return code;
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /**
     * 按数值解析。
     *
     * @param code 状态数值；{@code null} 返回 {@code null}
     * @return 匹配的枚举；未知返回 {@code null}（编码规范 §6.3 兜底，不抛异常）
     */
    public static TaskStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (TaskStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
