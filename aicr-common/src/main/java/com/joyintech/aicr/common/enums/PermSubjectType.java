package com.joyintech.aicr.common.enums;

/**
 * 数据权限主体类型。
 *
 * <p>契约出处：openapi {@code PermSubjectType}（enum: ROLE / DEPT）/
 * DBD {@code data_permission.subject_type}。
 * <p>使用方：aicr-security（数据权限拦截）、aicr-service（数据权限管理）、aicr-api。
 *
 * <p><b>命名消歧</b>（裁决 #11）：openapi 另有一个 {@code SubjectType}（enum: DEPT / PROJECT），
 * 属配额域，Java 侧命名为 {@code QuotaSubjectType}（P1，本轮不落地）。二者不可合并 ——
 * DBD §4.5 明确为两组不同的 CHECK 约束。
 */
public enum PermSubjectType {

    /** 按角色授权。 */
    ROLE("角色"),

    /** 按部门授权。 */
    DEPT("部门"),
    ;

    private final String label;

    PermSubjectType(String label) {
        this.label = label;
    }

    /** 主体类型码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 按主体类型码解析；未知或 {@code null} 返回 {@code null}。 */
    public static PermSubjectType of(String code) {
        if (code == null) {
            return null;
        }
        for (PermSubjectType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
