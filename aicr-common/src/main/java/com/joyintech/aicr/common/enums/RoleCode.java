package com.joyintech.aicr.common.enums;

/**
 * 角色标识。
 *
 * <p>契约出处：openapi {@code RoleCode} / LLD §7.2 / DBD {@code sys_role.role_code}。
 * <p>使用方：aicr-security（权限判定）、aicr-service（用户/角色管理）、aicr-web（管理端）、aicr-api。
 *
 * <p><b>⚠️ AUDITOR / OPERATOR 为 v1.0 预留角色，不启用</b>（LLD §7.2、开发分支管理规范 §4.5）：
 * 枚举<b>含</b>这两个值（openapi 已声明，DB CHECK 需一致），但
 * <b>禁止在权限判定分支中使用</b>。后续版本启用时须走 PR 同步 openapi 与 LLD。
 */
public enum RoleCode {

    /** 系统管理员。 */
    ADMIN("系统管理员"),

    /** 产品经理。 */
    PM("产品经理"),

    /** 测试人员。 */
    QA("测试人员"),

    /** 开发人员。 */
    DEVELOPER("开发人员"),

    /**
     * 审计员（<b>v1.0 不启用，禁止在权限判定分支中使用</b>）。
     */
    AUDITOR("审计员"),

    /**
     * 运维人员（<b>v1.0 不启用，禁止在权限判定分支中使用</b>）。
     */
    OPERATOR("运维人员"),
    ;

    private final String label;

    RoleCode(String label) {
        this.label = label;
    }

    /** 角色码字面量（= {@link #name()}，与 {@code sys_role.role_code} 列对齐）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /**
     * 按角色码解析。
     *
     * @param code 角色码（如 {@code "ADMIN"}）
     * @return 匹配的枚举；未知或 {@code null} 返回 {@code null}（编码规范 §6.3 兜底，不抛异常）
     */
    public static RoleCode of(String code) {
        if (code == null) {
            return null;
        }
        for (RoleCode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
