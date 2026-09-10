package com.joyintech.aicr.common.result;

import com.joyintech.aicr.common.constant.PageConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页响应 {@code {total, page, size, list}}。
 *
 * <p>契约出处：openapi {@code PageResultBase}（required: total/page/size）+ 13 个
 * {@code PageResultOfXxx}。约定：{@code list} 永不返回 null，无数据时为 {@code []}
 * （编码规范 §4.3）。
 *
 * <p>使用方：aicr-base（分页查询封装的返回类型）、aicr-service（组装）、
 * aicr-api（{@code data} 字段类型）。
 *
 * <p>注意：引用具体业务 Item 类型的 {@code PageResultOfXxx} 留在 aicr-api 的 {@code dto} 包，
 * common 只给泛型容器，不感知任何业务类型（准入判据 A3）。
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    /** 总记录数。 */
    private long total;

    /** 当前页码（1 起始）。 */
    private int page;

    /** 每页条数。 */
    private int size;

    /** 当前页数据；永不为 null。 */
    private List<T> list;

    /** 供子类（如 aicr-api 的 {@code PageResultOfXxx}）扩展使用。 */
    protected PageResult() {
    }

    PageResult(long total, int page, int size, List<T> list) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.list = list;
    }

    /**
     * 构造分页结果：{@code list == null} 归一为 {@code []}；
     * {@code page}/{@code size} 越界时兜底为 {@link PageConstant} 默认值，
     * {@code size} 超过 {@link PageConstant#MAX_SIZE} 时收敛到上限。
     */
    public static <T> PageResult<T> of(long total, int page, int size, List<T> list) {
        return new PageResult<>(total, normalizePage(page), normalizeSize(size), safeList(list));
    }

    /** 空结果：{@code page=1, size=20, total=0, list=[]}。 */
    public static <T> PageResult<T> empty() {
        return empty(PageConstant.DEFAULT_PAGE, PageConstant.DEFAULT_SIZE);
    }

    /** 空结果（指定页码与每页条数）。 */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(0L, normalizePage(page), normalizeSize(size), List.of());
    }

    /**
     * 元素类型转换（Entity → DTO）。
     *
     * <p>仅依赖 JDK {@link Function}，不引任何第三方（准入判据 A1）；
     * {@code total}/{@code page}/{@code size} 原样透传，不因转换而变化。
     *
     * @param mapper 元素转换函数；为 {@code null} 时返回空列表（防御式）
     * @param <R>    目标元素类型
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>();
        if (mapper != null && this.list != null) {
            for (T item : this.list) {
                mapped.add(mapper.apply(item));
            }
        }
        return new PageResult<>(this.total, this.page, this.size, mapped);
    }

    /** 计算总页数；{@code size <= 0} 返回 0（防除零）。 */
    public long getTotalPages() {
        if (this.size <= 0) {
            return 0L;
        }
        long pages = this.total / this.size;
        return (this.total % this.size == 0) ? pages : pages + 1;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public List<T> getList() {
        return list;
    }

    private static <T> List<T> safeList(List<T> list) {
        return (list == null) ? List.of() : list;
    }

    private static int normalizePage(int page) {
        return (page < PageConstant.MIN_PAGE) ? PageConstant.DEFAULT_PAGE : page;
    }

    private static int normalizeSize(int size) {
        int safe = (size < PageConstant.MIN_SIZE) ? PageConstant.DEFAULT_SIZE : size;
        return (safe > PageConstant.MAX_SIZE) ? PageConstant.MAX_SIZE : safe;
    }
}
