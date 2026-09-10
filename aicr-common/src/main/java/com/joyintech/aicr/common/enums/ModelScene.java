package com.joyintech.aicr.common.enums;

/**
 * 模型适用场景。
 *
 * <p>契约出处：openapi {@code ModelScene} / DBD {@code model_config.scene}。
 * <p>使用方：aicr-engine（按场景选择模型配置）、aicr-service（模型配置管理）、
 * aicr-web（管理端）、aicr-api。
 */
public enum ModelScene {

    /** 代码评审。 */
    REVIEW("评审"),

    /** 测试生成。 */
    TEST_GEN("测试生成"),
    ;

    private final String label;

    ModelScene(String label) {
        this.label = label;
    }

    /** 场景码字面量（= {@link #name()}）。 */
    public String code() {
        return name();
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 按场景码解析；未知或 {@code null} 返回 {@code null}。 */
    public static ModelScene of(String code) {
        if (code == null) {
            return null;
        }
        for (ModelScene value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
