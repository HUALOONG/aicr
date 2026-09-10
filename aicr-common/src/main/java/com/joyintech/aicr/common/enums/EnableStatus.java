package com.joyintech.aicr.common.enums;

/**
 * 通用启用状态（int 型枚举）。
 *
 * <p>契约出处：openapi {@code EnableStatus}（enum: 0/1）/ DBD 通用 {@code status} 列。
 * <p>使用方：aicr-service（平台配置 / 通知配置 / 规则 / 指令模板 等配置表的启停）、
 * aicr-web（管理端）、aicr-api。
 *
 * <p>注意：不要与 {@link TaskStatus}（任务主状态 0~4）混淆；本枚举仅 2 个值，用于「配置类」实体的启停。
 */
public enum EnableStatus {

    /** 停用。 */
    DISABLED(0, "停用"),

    /** 启用。 */
    ENABLED(1, "启用"),
    ;

    private final int code;
    private final String label;

    EnableStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 状态数值（与 DB {@code status} 列对齐）。 */
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
     * @return 匹配的枚举；未知返回 {@code null}（不抛异常）
     */
    public static EnableStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (EnableStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
