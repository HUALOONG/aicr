package com.joyintech.aicr.common.enums;

/**
 * 数据权限资源类型。
 *
 * <p>契约出处：openapi {@code ResourceType} / DBD {@code data_permission.resource_type}。
 * <p>使用方：aicr-security（数据权限拦截）、aicr-service（数据权限管理）、aicr-api。
 */
public enum ResourceType {

    /** 评审任务。 */
    REVIEW_TASK("评审任务"),

    /** 数据看板。 */
    DASHBOARD("数据看板"),

    /** 评审规则。 */
    RULE("评审规则"),
    ;

    private final String label;

    ResourceType(String label) {
        this.label = label;
    }

    /** 资源类型码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 按资源类型码解析；未知或 {@code null} 返回 {@code null}。 */
    public static ResourceType of(String code) {
        if (code == null) {
            return null;
        }
        for (ResourceType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
