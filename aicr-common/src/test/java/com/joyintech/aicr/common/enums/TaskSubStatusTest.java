package com.joyintech.aicr.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * { {@link TaskSubStatus} } 契约测试（<b>本模块最核心的测试</b>）。
 *
 * <p>覆盖：24 条合法流转逐条断言 / 非法组合断言 / 终态不可再流转 /
 * {@code isTerminal()} 与 {@code isRetryable()} 双口径 / {@code mainStatus()} 映射 /
 * {@code nextStatuses()} / {@code of()} 兜底。
 *
 * <p>流转表数据源：LLD §6.2（24 条，含 §6.4 三条受控回退），与设计说明书 §4.6 逐条一致。
 */
@DisplayName("TaskSubStatus 任务业务状态")
class TaskSubStatusTest {

    /** 24 条合法流转边（与设计说明书 §4.6 表格逐行对齐）。 */
    private static final TaskSubStatus[][] LEGAL_TRANSITIONS = {
            // RECEIVED → PARSING, SKIPPED_QUOTA
            {TaskSubStatus.RECEIVED, TaskSubStatus.PARSING},
            {TaskSubStatus.RECEIVED, TaskSubStatus.SKIPPED_QUOTA},
            // PARSING → QUEUED, FAILED
            {TaskSubStatus.PARSING, TaskSubStatus.QUEUED},
            {TaskSubStatus.PARSING, TaskSubStatus.FAILED},
            // QUEUED → ANALYZING, CANCELLED
            {TaskSubStatus.QUEUED, TaskSubStatus.ANALYZING},
            {TaskSubStatus.QUEUED, TaskSubStatus.CANCELLED},
            // ANALYZING → GENERATING_TEST, WRITING_BACK, RETRYING, FAILED, DEGRADED, CANCELLED
            {TaskSubStatus.ANALYZING, TaskSubStatus.GENERATING_TEST},
            {TaskSubStatus.ANALYZING, TaskSubStatus.WRITING_BACK},
            {TaskSubStatus.ANALYZING, TaskSubStatus.RETRYING},
            {TaskSubStatus.ANALYZING, TaskSubStatus.FAILED},
            {TaskSubStatus.ANALYZING, TaskSubStatus.DEGRADED},
            {TaskSubStatus.ANALYZING, TaskSubStatus.CANCELLED},
            // RETRYING → ANALYZING, FAILED
            {TaskSubStatus.RETRYING, TaskSubStatus.ANALYZING},
            {TaskSubStatus.RETRYING, TaskSubStatus.FAILED},
            // GENERATING_TEST → WRITING_BACK, PARTIAL_SUCCESS, CANCELLED
            {TaskSubStatus.GENERATING_TEST, TaskSubStatus.WRITING_BACK},
            {TaskSubStatus.GENERATING_TEST, TaskSubStatus.PARTIAL_SUCCESS},
            {TaskSubStatus.GENERATING_TEST, TaskSubStatus.CANCELLED},
            // WRITING_BACK → COMPLETED, PARTIAL_SUCCESS, TIMEOUT
            {TaskSubStatus.WRITING_BACK, TaskSubStatus.COMPLETED},
            {TaskSubStatus.WRITING_BACK, TaskSubStatus.PARTIAL_SUCCESS},
            {TaskSubStatus.WRITING_BACK, TaskSubStatus.TIMEOUT},
            // TIMEOUT → RETRYING, FAILED
            {TaskSubStatus.TIMEOUT, TaskSubStatus.RETRYING},
            {TaskSubStatus.TIMEOUT, TaskSubStatus.FAILED},
            // PARTIAL_SUCCESS → WRITING_BACK
            {TaskSubStatus.PARTIAL_SUCCESS, TaskSubStatus.WRITING_BACK},
            // DEGRADED → ANALYZING
            {TaskSubStatus.DEGRADED, TaskSubStatus.ANALYZING},
    };

    private static boolean isLegal(TaskSubStatus from, TaskSubStatus to) {
        for (TaskSubStatus[] edge : LEGAL_TRANSITIONS) {
            if (edge[0] == from && edge[1] == to) {
                return true;
            }
        }
        return false;
    }

    @Nested
    @DisplayName("枚举基础")
    class Basics {

        @Test
        @DisplayName("共 14 个业务状态")
        void exactlyFourteen() {
            assertEquals(14, TaskSubStatus.values().length);
        }

        @Test
        @DisplayName("mainStatus() 映射与 LLD §6.3 的 5↔14 分组一致")
        void mainStatus_mapping() {
            assertEquals(TaskStatus.PENDING, TaskSubStatus.RECEIVED.mainStatus());
            assertEquals(TaskStatus.PENDING, TaskSubStatus.PARSING.mainStatus());
            assertEquals(TaskStatus.PENDING, TaskSubStatus.QUEUED.mainStatus());

            assertEquals(TaskStatus.PROCESSING, TaskSubStatus.ANALYZING.mainStatus());
            assertEquals(TaskStatus.PROCESSING, TaskSubStatus.GENERATING_TEST.mainStatus());
            assertEquals(TaskStatus.PROCESSING, TaskSubStatus.WRITING_BACK.mainStatus());
            assertEquals(TaskStatus.PROCESSING, TaskSubStatus.RETRYING.mainStatus());
            assertEquals(TaskStatus.PROCESSING, TaskSubStatus.TIMEOUT.mainStatus());

            assertEquals(TaskStatus.COMPLETED, TaskSubStatus.COMPLETED.mainStatus());
            assertEquals(TaskStatus.COMPLETED, TaskSubStatus.PARTIAL_SUCCESS.mainStatus());
            assertEquals(TaskStatus.COMPLETED, TaskSubStatus.DEGRADED.mainStatus());

            assertEquals(TaskStatus.FAILED, TaskSubStatus.FAILED.mainStatus());

            assertEquals(TaskStatus.CANCELLED, TaskSubStatus.CANCELLED.mainStatus());
            assertEquals(TaskStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA.mainStatus());
        }

        @Test
        @DisplayName("label() 非空")
        void labelsNonEmpty() {
            for (TaskSubStatus value : TaskSubStatus.values()) {
                assertTrue(value.label() != null && !value.label().isEmpty(),
                        value.name() + " 的 label 不应为空");
            }
        }

        @Test
        @DisplayName("of() 已知值正确解析")
        void of_known() {
            assertEquals(TaskSubStatus.ANALYZING, TaskSubStatus.of("ANALYZING"));
            assertEquals(TaskSubStatus.SKIPPED_QUOTA, TaskSubStatus.of("SKIPPED_QUOTA"));
        }

        @Test
        @DisplayName("of() 未知值与 null 返回 null（编码规范 §6.3 兜底，不抛异常）")
        void of_unknownReturnsNull() {
            assertNull(TaskSubStatus.of("NOT_EXIST"));
            assertNull(TaskSubStatus.of("analyzing"));
            assertNull(TaskSubStatus.of(""));
            assertNull(TaskSubStatus.of(null));
        }
    }

    @Nested
    @DisplayName("24 条流转判定")
    class Transitions {

        @Test
        @DisplayName("表内恰好 24 条边")
        void transitionTableHas24Edges() {
            assertEquals(24, LEGAL_TRANSITIONS.length);
        }

        @Test
        @DisplayName("24 条合法流转逐条断言 canTransitionTo == true")
        void allLegalTransitionsAllowed() {
            for (TaskSubStatus[] edge : LEGAL_TRANSITIONS) {
                assertTrue(edge[0].canTransitionTo(edge[1]),
                        String.format("应为合法流转：%s → %s", edge[0], edge[1]));
            }
        }

        @Test
        @DisplayName("全部 196 个组合中，非 24 条合法边的组合一律返回 false")
        void allIllegalTransitionsRejected() {
            TaskSubStatus[] all = TaskSubStatus.values();
            for (TaskSubStatus from : all) {
                for (TaskSubStatus to : all) {
                    if (isLegal(from, to)) {
                        continue;
                    }
                    assertFalse(from.canTransitionTo(to),
                            String.format("应为非法流转：%s → %s", from, to));
                }
            }
        }

        @Test
        @DisplayName("canTransitionTo(null) 返回 false（防御式）")
        void nullTargetRejected() {
            for (TaskSubStatus from : TaskSubStatus.values()) {
                assertFalse(from.canTransitionTo(null));
            }
        }

        @Test
        @DisplayName("实际合法边数与设计表完全一致（无多余、无遗漏）")
        void actualEdgesMatchDesignTable() {
            int count = 0;
            TaskSubStatus[] all = TaskSubStatus.values();
            for (TaskSubStatus from : all) {
                for (TaskSubStatus to : all) {
                    if (from.canTransitionTo(to)) {
                        count++;
                    }
                }
            }
            assertEquals(24, count);
        }

        @Test
        @DisplayName("self-loop 一律非法（状态不允许原地自转）")
        void selfLoopRejected() {
            for (TaskSubStatus value : TaskSubStatus.values()) {
                assertFalse(value.canTransitionTo(value),
                        value.name() + " 不应允许自环流转");
            }
        }
    }

    @Nested
    @DisplayName("终态保护")
    class TerminalStates {

        @Test
        @DisplayName("4 个终态无合法后继")
        void terminalStatesHaveNoSuccessors() {
            TaskSubStatus[] terminals = {
                    TaskSubStatus.COMPLETED, TaskSubStatus.FAILED,
                    TaskSubStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA
            };
            for (TaskSubStatus terminal : terminals) {
                assertTrue(terminal.nextStatuses().isEmpty(),
                        terminal.name() + " 应无合法后继");
                for (TaskSubStatus to : TaskSubStatus.values()) {
                    assertFalse(terminal.canTransitionTo(to),
                            String.format("终态 %s 不应能流转到 %s", terminal, to));
                }
            }
        }

        @Test
        @DisplayName("终态重入保护：终态 → 终态 也一律非法")
        void terminalToTerminalRejected() {
            TaskSubStatus[] terminals = {
                    TaskSubStatus.COMPLETED, TaskSubStatus.FAILED,
                    TaskSubStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA
            };
            for (TaskSubStatus from : terminals) {
                for (TaskSubStatus to : terminals) {
                    assertFalse(from.canTransitionTo(to));
                }
            }
        }
    }

    @Nested
    @DisplayName("isTerminal() 与 isRetryable() 双口径")
    class DualCriteria {

        @Test
        @DisplayName("isTerminal()：MQ 丢弃口径 {COMPLETED, FAILED, CANCELLED}（LLD §10.4）")
        void isTerminal_mqDropCriteria() {
            assertTrue(TaskSubStatus.COMPLETED.isTerminal());
            assertTrue(TaskSubStatus.FAILED.isTerminal());
            assertTrue(TaskSubStatus.CANCELLED.isTerminal());

            // SKIPPED_QUOTA 虽为主状态 CANCELLED，但按 LLD §10.4 的 MQ 丢弃口径不计入
            assertFalse(TaskSubStatus.SKIPPED_QUOTA.isTerminal());

            for (TaskSubStatus value : TaskSubStatus.values()) {
                if (value != TaskSubStatus.COMPLETED
                        && value != TaskSubStatus.FAILED
                        && value != TaskSubStatus.CANCELLED) {
                    assertFalse(value.isTerminal(), value.name() + " 不应是终态");
                }
            }
        }

        @Test
        @DisplayName("isRetryable()：人工重试口径 {FAILED, TIMEOUT, DEGRADED, PARTIAL_SUCCESS}")
        void isRetryable_manualRetryCriteria() {
            assertTrue(TaskSubStatus.FAILED.isRetryable());
            assertTrue(TaskSubStatus.TIMEOUT.isRetryable());
            assertTrue(TaskSubStatus.DEGRADED.isRetryable());
            assertTrue(TaskSubStatus.PARTIAL_SUCCESS.isRetryable());

            for (TaskSubStatus value : TaskSubStatus.values()) {
                if (value != TaskSubStatus.FAILED
                        && value != TaskSubStatus.TIMEOUT
                        && value != TaskSubStatus.DEGRADED
                        && value != TaskSubStatus.PARTIAL_SUCCESS) {
                    assertFalse(value.isRetryable(), value.name() + " 不应可重试");
                }
            }
        }

        @Test
        @DisplayName("双口径交叉：FAILED 既是终态又可重试；TIMEOUT/DEGRADED/PARTIAL_SUCCESS 可重试但非终态")
        void dualCriteriaCrossCheck() {
            // FAILED：终态 ∩ 可重试
            assertTrue(TaskSubStatus.FAILED.isTerminal());
            assertTrue(TaskSubStatus.FAILED.isRetryable());

            // TIMEOUT / DEGRADED / PARTIAL_SUCCESS：可重试 ∖ 终态
            assertFalse(TaskSubStatus.TIMEOUT.isTerminal());
            assertTrue(TaskSubStatus.TIMEOUT.isRetryable());
            assertFalse(TaskSubStatus.DEGRADED.isTerminal());
            assertTrue(TaskSubStatus.DEGRADED.isRetryable());
            assertFalse(TaskSubStatus.PARTIAL_SUCCESS.isTerminal());
            assertTrue(TaskSubStatus.PARTIAL_SUCCESS.isRetryable());

            // COMPLETED / CANCELLED：终态 ∖ 可重试
            assertFalse(TaskSubStatus.COMPLETED.isRetryable());
            assertFalse(TaskSubStatus.CANCELLED.isRetryable());
        }

        @Test
        @DisplayName("terminalStatuses() 集合口径与 isTerminal() 一致")
        void terminalStatuses_set() {
            Set<TaskSubStatus> terminals = TaskSubStatus.terminalStatuses();
            assertEquals(3, terminals.size());
            assertTrue(terminals.contains(TaskSubStatus.COMPLETED));
            assertTrue(terminals.contains(TaskSubStatus.FAILED));
            assertTrue(terminals.contains(TaskSubStatus.CANCELLED));
            assertFalse(terminals.contains(TaskSubStatus.SKIPPED_QUOTA));

            for (TaskSubStatus value : TaskSubStatus.values()) {
                assertEquals(value.isTerminal(), terminals.contains(value),
                        value.name() + " 的 isTerminal() 与 terminalStatuses() 口径应一致");
            }
        }

        @Test
        @DisplayName("retryableStatuses() 集合口径与 isRetryable() 一致")
        void retryableStatuses_set() {
            Set<TaskSubStatus> retryables = TaskSubStatus.retryableStatuses();
            assertEquals(4, retryables.size());

            for (TaskSubStatus value : TaskSubStatus.values()) {
                assertEquals(value.isRetryable(), retryables.contains(value),
                        value.name() + " 的 isRetryable() 与 retryableStatuses() 口径应一致");
            }
        }
    }

    @Nested
    @DisplayName("nextStatuses()")
    class NextStatuses {

        @Test
        @DisplayName("nextStatuses() 返回的集合与 canTransitionTo 判定完全一致")
        void nextStatuses_matchesCanTransitionTo() {
            TaskSubStatus[] all = TaskSubStatus.values();
            for (TaskSubStatus from : all) {
                Set<TaskSubStatus> next = from.nextStatuses();
                for (TaskSubStatus to : all) {
                    assertEquals(from.canTransitionTo(to), next.contains(to),
                            String.format("%s → %s 的两个口径不一致", from, to));
                }
            }
        }

        @Test
        @DisplayName("ANALYZING 的 6 个合法后继")
        void analyzing_hasSixNext() {
            Set<TaskSubStatus> next = TaskSubStatus.ANALYZING.nextStatuses();
            assertEquals(6, next.size());
            assertTrue(next.contains(TaskSubStatus.GENERATING_TEST));
            assertTrue(next.contains(TaskSubStatus.WRITING_BACK));
            assertTrue(next.contains(TaskSubStatus.RETRYING));
            assertTrue(next.contains(TaskSubStatus.FAILED));
            assertTrue(next.contains(TaskSubStatus.DEGRADED));
            assertTrue(next.contains(TaskSubStatus.CANCELLED));
        }

        @Test
        @DisplayName("nextStatuses() 不随重复调用而变化（不可变）")
        void nextStatuses_immutableAcrossCalls() {
            Set<TaskSubStatus> first = TaskSubStatus.PARSING.nextStatuses();
            first.add(TaskSubStatus.COMPLETED);
            Set<TaskSubStatus> second = TaskSubStatus.PARSING.nextStatuses();
            assertFalse(second.contains(TaskSubStatus.COMPLETED));
            assertEquals(2, second.size());
        }

        @Test
        @DisplayName("RECEIVED 后继为 {PARSING, SKIPPED_QUOTA}")
        void received_next() {
            Set<TaskSubStatus> next = TaskSubStatus.RECEIVED.nextStatuses();
            assertEquals(2, next.size());
            assertTrue(next.contains(TaskSubStatus.PARSING));
            assertTrue(next.contains(TaskSubStatus.SKIPPED_QUOTA));
        }
    }
}
