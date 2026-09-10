package com.joyintech.aicr.common.result;

import com.joyintech.aicr.common.constant.PageConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageResult} 契约测试。
 *
 * <p>覆盖：null 归一 / 空结果默认值 / map 转换 / 总页数防除零 / 边界收敛。
 */
@DisplayName("PageResult 分页响应")
class PageResultTest {

    @Nested
    @DisplayName("of() 工厂")
    class Of {

        @Test
        @DisplayName("list=null 归一为 []（永不返回 null，编码规范 §4.3）")
        void list_nullBecomesEmpty() {
            PageResult<String> pr = PageResult.of(5L, 1, 20, null);
            assertNotNull(pr.getList());
            assertTrue(pr.getList().isEmpty());
            assertEquals(5L, pr.getTotal());
        }

        @Test
        @DisplayName("list 非空时原样透传")
        void list_preserved() {
            List<String> list = List.of("a", "b", "c");
            PageResult<String> pr = PageResult.of(3L, 1, 20, list);
            assertEquals(list, pr.getList());
            assertEquals(3, pr.getList().size());
        }

        @Test
        @DisplayName("page 越界兜底为默认页码")
        void page_belowMinFallsBackToDefault() {
            PageResult<String> pr = PageResult.of(0L, 0, 20, List.of());
            assertEquals(PageConstant.DEFAULT_PAGE, pr.getPage());
        }

        @Test
        @DisplayName("size 越界兜底为默认每页条数")
        void size_belowMinFallsBackToDefault() {
            PageResult<String> pr = PageResult.of(0L, 1, 0, List.of());
            assertEquals(PageConstant.DEFAULT_SIZE, pr.getSize());
        }

        @Test
        @DisplayName("size 超过上限收敛到 MAX_SIZE")
        void size_aboveMaxClamped() {
            PageResult<String> pr = PageResult.of(0L, 1, 10000, List.of());
            assertEquals(PageConstant.MAX_SIZE, pr.getSize());
        }

        @Test
        @DisplayName("size 在合法区间时原样保留")
        void size_withinRangeKept() {
            PageResult<String> pr = PageResult.of(10L, 2, 50, List.of());
            assertEquals(50, pr.getSize());
            assertEquals(2, pr.getPage());
        }
    }

    @Nested
    @DisplayName("empty() 工厂")
    class Empty {

        @Test
        @DisplayName("empty() 默认 page=1、size=20、total=0、list=[]")
        void empty_defaults() {
            PageResult<String> pr = PageResult.empty();
            assertEquals(0L, pr.getTotal());
            assertEquals(1, pr.getPage());
            assertEquals(20, pr.getSize());
            assertNotNull(pr.getList());
            assertTrue(pr.getList().isEmpty());
        }

        @Test
        @DisplayName("empty(page, size) 使用给定分页参数")
        void empty_withParams() {
            PageResult<String> pr = PageResult.empty(3, 50);
            assertEquals(0L, pr.getTotal());
            assertEquals(3, pr.getPage());
            assertEquals(50, pr.getSize());
            assertTrue(pr.getList().isEmpty());
        }
    }

    @Nested
    @DisplayName("map() 元素转换")
    class Map {

        @Test
        @DisplayName("map() 转换元素且 total/page/size 不变")
        void map_convertsElementsAndKeepsMeta() {
            PageResult<String> source = PageResult.of(3L, 2, 10, List.of("a", "b", "c"));
            PageResult<Integer> target = source.map(String::length);

            assertEquals(List.of(1, 1, 1), target.getList());
            assertEquals(source.getTotal(), target.getTotal());
            assertEquals(source.getPage(), target.getPage());
            assertEquals(source.getSize(), target.getSize());
            assertEquals(2, target.getPage());
            assertEquals(10, target.getSize());
        }

        @Test
        @DisplayName("map() 不修改源对象（不可变语义）")
        void map_doesNotMutateSource() {
            PageResult<String> source = PageResult.of(2L, 1, 20, List.of("aa", "bb"));
            source.map(String::length);

            assertEquals(List.of("aa", "bb"), source.getList());
            assertEquals(2L, source.getTotal());
        }

        @Test
        @DisplayName("map() 空列表返回空列表")
        void map_emptyList() {
            PageResult<String> source = PageResult.of(0L, 1, 20, List.of());
            PageResult<String> target = source.map(String::toUpperCase);
            assertTrue(target.getList().isEmpty());
        }

        @Test
        @DisplayName("map(null) 防御式返回空列表，不抛 NPE")
        void map_nullMapperDefensive() {
            PageResult<String> source = PageResult.of(2L, 1, 20, List.of("a", "b"));
            PageResult<Object> target = source.map(null);
            assertTrue(target.getList().isEmpty());
            assertEquals(2L, target.getTotal());
        }
    }

    @Nested
    @DisplayName("getTotalPages() 总页数")
    class TotalPages {

        @Test
        @DisplayName("size 为 0 时返回 0（防除零）")
        void sizeZeroReturnsZero() {
            PageResult<String> pr = new PageResult<>(100L, 1, 0, List.of());
            assertEquals(0L, pr.getTotalPages());
        }

        @Test
        @DisplayName("total 为 0 时返回 0")
        void totalZeroReturnsZero() {
            PageResult<String> pr = PageResult.of(0L, 1, 20, List.of());
            assertEquals(0L, pr.getTotalPages());
        }

        @Test
        @DisplayName("整除时不加 1")
        void exactDivision() {
            PageResult<String> pr = PageResult.of(40L, 1, 20, List.of());
            assertEquals(2L, pr.getTotalPages());
        }

        @Test
        @DisplayName("不整除时进位 1")
        void ceilingDivision() {
            PageResult<String> pr = PageResult.of(41L, 1, 20, List.of());
            assertEquals(3L, pr.getTotalPages());
        }

        @Test
        @DisplayName("total 小于 size 时为 1 页")
        void singlePage() {
            PageResult<String> pr = PageResult.of(3L, 1, 20, List.of());
            assertEquals(1L, pr.getTotalPages());
        }
    }
}
