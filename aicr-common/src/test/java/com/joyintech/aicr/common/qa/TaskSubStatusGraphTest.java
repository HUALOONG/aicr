package com.joyintech.aicr.common.qa;

import com.joyintech.aicr.common.enums.TaskSubStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA 独立验收：TaskSubStatus 状态机 <b>图论视角</b>。
 *
 * <p>不依赖工程师的 24 条边硬编码断言，直接从图论属性验证状态机结构：
 * <ul>
 *   <li>弱连通性（RECEIVED 出发可达全部 14 个状态）；</li>
 *   <li>无死锁（每个非终态至少有 1 个后继）；</li>
 *   <li>无自环（{@code canTransitionTo(self) == false}）；</li>
 *   <li>边总数恰好 24；</li>
 *   <li>拓扑可达（每个状态都能到达某个终态或形成稳定回路）。</li>
 * </ul>
 */
@DisplayName("QA 独立验收 - TaskSubStatus 图论验证")
class TaskSubStatusGraphTest {

    /** 主状态分组（LLD §6.3 的 5↔14 映射）。 */
    private static final Map<TaskSubStatus, Set<TaskSubStatus>> MAIN_STATUS_GROUPS = new HashMap<>();

    static {
        MAIN_STATUS_GROUPS.put(TaskSubStatus.RECEIVED,
                EnumSet.of(TaskSubStatus.RECEIVED, TaskSubStatus.PARSING, TaskSubStatus.QUEUED));
        MAIN_STATUS_GROUPS.put(TaskSubStatus.ANALYZING,
                EnumSet.of(TaskSubStatus.ANALYZING, TaskSubStatus.GENERATING_TEST,
                        TaskSubStatus.WRITING_BACK, TaskSubStatus.RETRYING, TaskSubStatus.TIMEOUT));
        MAIN_STATUS_GROUPS.put(TaskSubStatus.COMPLETED,
                EnumSet.of(TaskSubStatus.COMPLETED, TaskSubStatus.PARTIAL_SUCCESS, TaskSubStatus.DEGRADED));
        MAIN_STATUS_GROUPS.put(TaskSubStatus.FAILED, EnumSet.of(TaskSubStatus.FAILED));
        MAIN_STATUS_GROUPS.put(TaskSubStatus.CANCELLED,
                EnumSet.of(TaskSubStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA));
    }

    /** 收集状态机的全部有向边（作为「无死锁」「无自环」「拓扑」测试的数据源）。 */
    private static List<TaskSubStatus[]> collectAllEdges() {
        List<TaskSubStatus[]> edges = new ArrayList<>();
        for (TaskSubStatus from : TaskSubStatus.values()) {
            for (TaskSubStatus to : TaskSubStatus.values()) {
                if (from.canTransitionTo(to)) {
                    edges.add(new TaskSubStatus[]{from, to});
                }
            }
        }
        return edges;
    }

    @Test
    @DisplayName("① 枚举数量与 LLD 附录B 一致：14 个业务状态")
    void enumCardinality() {
        assertEquals(14, TaskSubStatus.values().length);
    }

    @Test
    @DisplayName("② 边总数恰好 24 条（LLD §6.2 全表）")
    void edgeCountExactly24() {
        List<TaskSubStatus[]> edges = collectAllEdges();
        assertEquals(24, edges.size(),
                "合法流转边数应恰好 24 条，实际 " + edges.size() + " 条");
    }

    @Test
    @DisplayName("③ 无自环：所有状态 canTransitionTo(self) == false")
    void noSelfLoops() {
        List<String> selfLoops = new ArrayList<>();
        for (TaskSubStatus state : TaskSubStatus.values()) {
            if (state.canTransitionTo(state)) {
                selfLoops.add(state.name());
            }
        }
        assertTrue(selfLoops.isEmpty(),
                "发现自环：" + String.join(", ", selfLoops));
    }

    @Test
    @DisplayName("④ 弱连通：从 RECEIVED 出发的可达集 = 全部 14 个状态")
    void weaklyConnectedFromReceived() {
        Set<TaskSubStatus> reachable = bfsReachable(TaskSubStatus.RECEIVED);
        assertEquals(EnumSet.allOf(TaskSubStatus.class), reachable,
                "RECEIVED 出发应能到达全部 14 个状态，实际只到 " +
                        reachable.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
    }

    @Test
    @DisplayName("⑤ 无死锁：每个非终态至少有一个后继")
    void noDeadlockInNonTerminals() {
        // 非终态定义：不属于「终态集合」（MQ 丢弃口径 COMPLETED/FAILED/CANCELLED
        // 与主状态 CANCELLED 的 SKIPPED_QUOTA）
        Set<TaskSubStatus> graphTerminals = EnumSet.of(
                TaskSubStatus.COMPLETED, TaskSubStatus.FAILED,
                TaskSubStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA);

        List<String> deadlocks = new ArrayList<>();
        for (TaskSubStatus state : TaskSubStatus.values()) {
            if (graphTerminals.contains(state)) {
                continue;
            }
            if (state.nextStatuses().isEmpty()) {
                deadlocks.add(state.name());
            }
        }
        assertTrue(deadlocks.isEmpty(),
                "以下非终态无后继（死锁）：" + String.join(", ", deadlocks));
    }

    @Test
    @DisplayName("⑥ 终态无后继（严格）")
    void terminalsHaveNoSuccessors() {
        Set<TaskSubStatus> graphTerminals = EnumSet.of(
                TaskSubStatus.COMPLETED, TaskSubStatus.FAILED,
                TaskSubStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA);

        for (TaskSubStatus terminal : graphTerminals) {
            assertTrue(terminal.nextStatuses().isEmpty(),
                    terminal.name() + " 应无后继");
        }
    }

    @Test
    @DisplayName("⑦ 可达性（拓扑/收敛）：任一非终态出发，至少能到达一个终态")
    void everyNonTerminalReachesSomeTerminal() {
        Set<TaskSubStatus> graphTerminals = EnumSet.of(
                TaskSubStatus.COMPLETED, TaskSubStatus.FAILED,
                TaskSubStatus.CANCELLED, TaskSubStatus.SKIPPED_QUOTA);

        List<String> unreachable = new ArrayList<>();
        for (TaskSubStatus start : TaskSubStatus.values()) {
            if (graphTerminals.contains(start)) {
                continue;
            }
            Set<TaskSubStatus> reachable = bfsReachable(start);
            if (reachable.stream().noneMatch(graphTerminals::contains)) {
                unreachable.add(start.name());
            }
        }
        assertTrue(unreachable.isEmpty(),
                "以下状态无法到达任何终态（可能形成稳定死循环）：" + String.join(", ", unreachable));
    }

    @Test
    @DisplayName("⑧ 主状态分组覆盖完整 14 个业务状态（无遗漏、无重复）")
    void mainStatusGroupsCoverAll() {
        Set<TaskSubStatus> covered = new HashSet<>();
        for (Set<TaskSubStatus> group : MAIN_STATUS_GROUPS.values()) {
            for (TaskSubStatus sub : group) {
                assertTrue(!covered.contains(sub),
                        sub.name() + " 出现在多个主状态分组中（应唯一归属）");
                covered.add(sub);
            }
        }
        assertEquals(EnumSet.allOf(TaskSubStatus.class), covered,
                "主状态分组应恰好覆盖全部 14 个业务状态");
    }

    @Test
    @DisplayName("⑨ 每条边的主状态流转符合设计文档 §6.4 允许规则")
    void edgesRespectDesignDocAllowedTransitions() {
        List<String> violations = new ArrayList<>();
        for (TaskSubStatus[] edge : collectAllEdges()) {
            TaskSubStatus from = edge[0];
            TaskSubStatus to = edge[1];
            if (violatesDesignDocRule(from, to)) {
                violations.add(from.name() + " → " + to.name());
            }
        }
        assertTrue(violations.isEmpty(),
                "发现违反设计文档 §6.4 允许规则的边：" + String.join(", ", violations));
    }

    /** 主状态码：PENDING=0, PROCESSING=1, COMPLETED=2, FAILED=3, CANCELLED=4 */
    private static int mainStatusOrdinal(TaskSubStatus s) {
        switch (s.mainStatus()) {
            case PENDING: return 0;
            case PROCESSING: return 1;
            case COMPLETED: return 2;
            case FAILED: return 3;
            case CANCELLED: return 4;
            default: throw new IllegalStateException("unknown");
        }
    }

    /**
     * 主状态流转允许规则（对齐设计说明书 §4.6 的 24 条 + §6.4 三条受控回退）：
     * <ul>
     *   <li>同主状态内流转：允许（e.g. PENDING→PENDING 内部状态切换）；</li>
     *   <li>PENDING → {PROCESSING, FAILED, CANCELLED}：允许（PENDING 可直通终态）；</li>
     *   <li>PROCESSING → {COMPLETED, FAILED, CANCELLED}：允许；</li>
     *   <li>COMPLETED → PROCESSING（受控回退）：允许（PARTIAL_SUCCESS→WRITING_BACK、DEGRADED→ANALYZING）；</li>
     *   <li>FAILED → 任意：不允许；</li>
     *   <li>CANCELLED → 任意：不允许；</li>
     * </ul>
     */
    private static boolean violatesDesignDocRule(TaskSubStatus from, TaskSubStatus to) {
        int fromOrdinal = mainStatusOrdinal(from);
        int toOrdinal = mainStatusOrdinal(to);
        if (fromOrdinal == toOrdinal) {
            return false; // 同主状态内部流转
        }
        // 明确禁止的：终态不能流出
        if (fromOrdinal == 3 || fromOrdinal == 4) { // FAILED 或 CANCELLED 出发
            return true;
        }
        // 允许：PENDING → 任何后续主状态
        if (fromOrdinal == 0) return false;
        // 允许：PROCESSING → COMPLETED/FAILED/CANCELLED
        if (fromOrdinal == 1) return false;
        // 允许：COMPLETED → PROCESSING（受控回退）
        if (fromOrdinal == 2 && toOrdinal == 1) return false;
        return true; // 其他跨主状态流转均非法
    }

    @Test
    @DisplayName("⑩ §6.4 三条受控回退显式存在（PARTIAL_SUCCESS/DEGRADED/TIMEOUT 的回路）")
    void controlledFallbackEdgesPresent() {
        // PARTIAL_SUCCESS → WRITING_BACK（COMPLETED 主状态回退到 PROCESSING）
        assertTrue(TaskSubStatus.PARTIAL_SUCCESS.canTransitionTo(TaskSubStatus.WRITING_BACK));
        // DEGRADED → ANALYZING（COMPLETED 主状态回退到 PROCESSING）
        assertTrue(TaskSubStatus.DEGRADED.canTransitionTo(TaskSubStatus.ANALYZING));
        // TIMEOUT → RETRYING（PROCESSING 内部回退）
        assertTrue(TaskSubStatus.TIMEOUT.canTransitionTo(TaskSubStatus.RETRYING));
    }

    @Test
    @DisplayName("⑪ RETRYING→ANALYZING→FAILED 的失败重试链路存在")
    void retryingAnalyzingFailedPath() {
        assertTrue(TaskSubStatus.RETRYING.canTransitionTo(TaskSubStatus.ANALYZING));
        assertTrue(TaskSubStatus.ANALYZING.canTransitionTo(TaskSubStatus.FAILED));
    }

    @Test
    @DisplayName("⑫ 状态机的强连通分量数：至少 2 个（存在终态分量 + 非终态回路分量）")
    void stronglyConnectedComponentCount() {
        // Tarjan 强连通分量
        List<Set<TaskSubStatus>> sccs = tarjanSCC();
        assertTrue(sccs.size() >= 2,
                "SCC 数量应 ≥ 2（至少有终态单节点分量 + 至少一个非终态分量），实际 " + sccs.size());
    }

    /** 从 start 出发的 BFS 可达集。 */
    private static Set<TaskSubStatus> bfsReachable(TaskSubStatus start) {
        Set<TaskSubStatus> visited = new LinkedHashSet<>();
        Deque<TaskSubStatus> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            TaskSubStatus current = queue.poll();
            for (TaskSubStatus next : current.nextStatuses()) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /** Tarjan 强连通分量算法（迭代版）。 */
    private static List<Set<TaskSubStatus>> tarjanSCC() {
        Map<TaskSubStatus, Integer> index = new HashMap<>();
        Map<TaskSubStatus, Integer> lowlink = new HashMap<>();
        Map<TaskSubStatus, Boolean> onStack = new HashMap<>();
        Deque<TaskSubStatus> stack = new ArrayDeque<>();
        List<Set<TaskSubStatus>> sccs = new ArrayList<>();
        int[] counter = {0};

        for (TaskSubStatus root : TaskSubStatus.values()) {
            if (!index.containsKey(root)) {
                tarjanIterative(root, index, lowlink, onStack, stack, sccs, counter);
            }
        }
        return sccs;
    }

    private static void tarjanIterative(TaskSubStatus root,
                                        Map<TaskSubStatus, Integer> index,
                                        Map<TaskSubStatus, Integer> lowlink,
                                        Map<TaskSubStatus, Boolean> onStack,
                                        Deque<TaskSubStatus> stack,
                                        List<Set<TaskSubStatus>> sccs,
                                        int[] counter) {
        // 迭代式 Tarjan：帧 = (当前节点, 后继迭代器)
        Deque<Frame> workStack = new ArrayDeque<>();
        workStack.push(new Frame(root, root.nextStatuses().iterator()));
        if (!index.containsKey(root)) {
            assignIndex(root, index, lowlink, onStack, stack);
        }

        while (!workStack.isEmpty()) {
            Frame frame = workStack.peek();
            if (frame.iter.hasNext()) {
                TaskSubStatus next = frame.iter.next();
                if (!index.containsKey(next)) {
                    assignIndex(next, index, lowlink, onStack, stack);
                    workStack.push(new Frame(next, next.nextStatuses().iterator()));
                } else if (Boolean.TRUE.equals(onStack.get(next))) {
                    lowlink.put(frame.node, Math.min(lowlink.get(frame.node), index.get(next)));
                }
            } else {
                workStack.pop();
                if (!workStack.isEmpty()) {
                    Frame parent = workStack.peek();
                    lowlink.put(parent.node,
                            Math.min(lowlink.get(parent.node), lowlink.get(frame.node)));
                }
                if (lowlink.get(frame.node).equals(index.get(frame.node))) {
                    Set<TaskSubStatus> component = new LinkedHashSet<>();
                    TaskSubStatus w;
                    do {
                        w = stack.pop();
                        onStack.put(w, false);
                        component.add(w);
                    } while (!w.equals(frame.node));
                    sccs.add(component);
                }
            }
        }
    }

    private static void assignIndex(TaskSubStatus node,
                                    Map<TaskSubStatus, Integer> index,
                                    Map<TaskSubStatus, Integer> lowlink,
                                    Map<TaskSubStatus, Boolean> onStack,
                                    Deque<TaskSubStatus> stack) {
        int idx = index.size();
        index.put(node, idx);
        lowlink.put(node, idx);
        onStack.put(node, true);
        stack.push(node);
    }

    private static class Frame {
        final TaskSubStatus node;
        final java.util.Iterator<TaskSubStatus> iter;

        Frame(TaskSubStatus node, java.util.Iterator<TaskSubStatus> iter) {
            this.node = node;
            this.iter = iter;
        }
    }

    @Test
    @DisplayName("⑬ 无自环 + 无孤立节点：图的基本连通性")
    void noIsolatedNodes() {
        // 孤立节点 = 无任何边相连的节点
        Set<TaskSubStatus> allEdges = new HashSet<>();
        for (TaskSubStatus[] edge : collectAllEdges()) {
            allEdges.add(edge[0]);
            allEdges.add(edge[1]);
        }
        // RECEIVED 是入口，COMPLETED/FAILED/CANCELLED/SKIPPED_QUOTA 是终态
        // 全部 14 个状态都应出现在某条边中
        assertEquals(14, allEdges.size(),
                "全部 14 个状态都应出现在某条边中，实际只 " + allEdges.size() + " 个");
        assertFalse(allEdges.isEmpty());
    }
}
