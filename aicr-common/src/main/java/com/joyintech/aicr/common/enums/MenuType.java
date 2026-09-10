package com.joyintech.aicr.common.enums;

/**
 * 菜单类型（int 型枚举）。
 *
 * <p>契约出处：DBD §4.24 {@code sys_menu.menu_type}（1/2/3）。openapi 未定义该 schema，
 * 取值以 DBD 为准（设计说明书 §3.2 标注 "—"）。
 * <p>使用方：aicr-service（菜单管理）、aicr-web（管理端菜单树）、aicr-security（按钮级权限）。
 */
public enum MenuType {

    /** 目录（无对应路由）。 */
    DIRECTORY(1, "目录"),

    /** 菜单（对应一个页面路由）。 */
    MENU(2, "菜单"),

    /** 按钮（对应一个操作权限点）。 */
    BUTTON(3, "按钮"),
    ;

    private final int code;
    private final String label;

    MenuType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 类型数值（与 {@code sys_menu.menu_type} 列对齐）。 */
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
     * @param code 类型数值；{@code null} 返回 {@code null}
     * @return 匹配的枚举；未知返回 {@code null}（不抛异常）
     */
    public static MenuType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (MenuType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
