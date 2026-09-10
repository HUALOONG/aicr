package com.joyintech.aicr.common.enums;

/**
 * 通知渠道。
 *
 * <p>契约出处：openapi {@code NotifyChannel} / DBD {@code notification_config.channel}。
 * <p>使用方：aicr-service（通知配置管理）、aicr-web（管理端）、aicr-worker（通知发送）、aicr-api。
 */
public enum NotifyChannel {

    /** 企业微信。 */
    WECOM("企业微信"),

    /** 钉钉。 */
    DINGTALK("钉钉"),

    /** 飞书。 */
    FEISHU("飞书"),

    /** 邮件。 */
    EMAIL("邮件"),
    ;

    private final String label;

    NotifyChannel(String label) {
        this.label = label;
    }

    /** 渠道码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 按渠道码解析；未知或 {@code null} 返回 {@code null}。 */
    public static NotifyChannel of(String code) {
        if (code == null) {
            return null;
        }
        for (NotifyChannel value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
