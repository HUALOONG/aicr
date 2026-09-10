package com.joyintech.aicr.common.enums;

/**
 * 用户同步来源。
 *
 * <p>契约出处：openapi {@code UserSyncParam.source}（enum: LDAP / OIDC），FR-014 用户同步。
 * <p>使用方：aicr-security（用户同步）、aicr-service（用户管理）、aicr-web（管理端）、aicr-api。
 */
public enum UserSource {

    /** LDAP 目录同步。 */
    LDAP("LDAP"),

    /** OIDC 单点登录同步。 */
    OIDC("OIDC"),
    ;

    private final String label;

    UserSource(String label) {
        this.label = label;
    }

    /** 来源码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 展示名。 */
    public String label() {
        return label;
    }

    /** 按来源码解析；未知或 {@code null} 返回 {@code null}。 */
    public static UserSource of(String code) {
        if (code == null) {
            return null;
        }
        for (UserSource value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
