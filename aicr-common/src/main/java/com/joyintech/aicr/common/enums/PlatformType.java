package com.joyintech.aicr.common.enums;

/**
 * 代码托管平台类型。
 *
 * <p>契约出处：openapi {@code PlatformType} / DBD {@code platform_config.platform_type}。
 * <p>使用方：aicr-webhook（Webhook 解析策略选择）、aicr-engine（Diff API 形态）、
 * aicr-service（平台配置管理）、aicr-web（管理端）、aicr-api。
 */
public enum PlatformType {

    /** GitLab（含 GitLab CE/EE）。 */
    GITLAB("GitLab"),

    /** GitHub。 */
    GITHUB("GitHub"),

    /** Gitea（含 Forgejo 等衍生）。 */
    GITEA("Gitea"),
    ;

    private final String label;

    PlatformType(String label) {
        this.label = label;
    }

    /** 平台码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 展示名。 */
    public String label() {
        return label;
    }

    /** 按平台码解析；未知或 {@code null} 返回 {@code null}。 */
    public static PlatformType of(String code) {
        if (code == null) {
            return null;
        }
        for (PlatformType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
