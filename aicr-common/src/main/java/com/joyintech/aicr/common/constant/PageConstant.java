package com.joyintech.aicr.common.constant;

/**
 * 分页边界常量。
 *
 * <p>契约出处：编码规范 §3（分页约定：page≥1、size 1~100、默认 20）、
 * openapi {@code PageRequest} / {@code PageResultBase}。
 * <p>使用方：aicr-web（入参校验）、aicr-base（分页查询封装）、
 * aicr-service（组装）、aicr-api（DTO 字段默认值）。
 *
 * <p>约定：无结果时返回 {@code list: []} 且 {@code total: 0}，不用 404（编码规范 §4.3）。
 */
public final class PageConstant {

    /** 页码最小值（1 起始，非 0 起始）。 */
    public static final int MIN_PAGE = 1;

    /** 默认页码。 */
    public static final int DEFAULT_PAGE = 1;

    /** 每页条数最小值。 */
    public static final int MIN_SIZE = 1;

    /** 默认每页条数。 */
    public static final int DEFAULT_SIZE = 20;

    /** 每页条数最大值（防止一次拉取过大数据集打垮引擎）。 */
    public static final int MAX_SIZE = 100;

    private PageConstant() {
    }
}
