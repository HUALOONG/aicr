package com.joyintech.aicr.common.enums;

/**
 * 数据可见范围。
 *
 * <p>契约出处：openapi {@code DataScope} / DBD {@code data_permission.scope}。
 * <p>使用方：aicr-security（数据权限拦截）、aicr-service（数据权限管理）、aicr-api。
 */
public enum DataScope {

    /** 全部数据。 */
    ALL("全部"),

    /** 本部门及下级部门。 */
    DEPT_AND_SUB("本部门及下级"),

    /** 仅本人数据。 */
    SELF("仅本人"),
    ;

    private final String label;

    DataScope(String label) {
        this.label = label;
    }

    /** 范围码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 按范围码解析；未知或 {@code null} 返回 {@code null}。 */
    public static DataScope of(String code) {
        if (code == null) {
            return null;
        }
        for (DataScope value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
