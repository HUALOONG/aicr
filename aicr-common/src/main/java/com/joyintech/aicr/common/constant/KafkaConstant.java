package com.joyintech.aicr.common.constant;

/**
 * Kafka 常量（裁决 #12 后语义收窄）。
 *
 * <p><b>重要语义变更</b>：裁决 #12 后，topic <b>不再是全局常量</b>，而是
 * 「仓库级数据库配置 + 事件类型后缀派生」（设计说明书 §3.4）。
 * 本类<b>只保留四类内容</b>：
 * <ol>
 *   <li>① 事件类型后缀：仓库 topic 基名 + 后缀 = 实际 topic；</li>
 *   <li>② 系统级 topic（不按仓库拆分）+ 兜底默认 topic 基名；</li>
 *   <li>③ topic 命名规范正则（worker 正则订阅与 DB CHECK 同源）；</li>
 *   <li>④ 分区键模板。</li>
 * </ol>
 * 仓库级 topic 的<b>取数</b>在 {@code aicr-base.repository}，<b>缓存</b>走
 * {@link RedisKeyConstant#CFG_REPO_MQ_TOPIC}（见设计说明书 §3.4）。
 *
 * <p>契约出处：LLD §10.1 / §10.3。
 * <p>使用方：aicr-webhook（生产）、aicr-worker（消费）、aicr-web（重试/反馈）、aicr-base.mq。
 */
public final class KafkaConstant {

    // ── ① 事件类型后缀：仓库 topic 基名 + 后缀 = 实际 topic ──────────
    //    依据 LLD §10.1：REVIEW_REQUEST / REVIEW_RETRY / WRITEBACK 的分区键为
    //    {platformConfigId}_{repoId}，属「按仓库分区」，必须与仓库级基名派生。

    /** 评审请求事件后缀。 */
    public static final String SUFFIX_REVIEW_REQUEST = ".request";

    /** 评审重试事件后缀。 */
    public static final String SUFFIX_REVIEW_RETRY = ".retry";

    /** 回写事件后缀。 */
    public static final String SUFFIX_WRITEBACK = ".writeback";

    // ── ② 系统级 topic（不按仓库拆分，全局唯一）──────────────────
    //    依据 LLD §10.1：FEEDBACK 分区键为 {taskId}、NOTIFY 分区键为 {channel}、
    //    DLQ 无分区键，三者均非「按仓库分区」，拆到仓库级会破坏顺序性且使 topic 数爆炸
    //    → 保持全局静态订阅（@KafkaListener(topics = 常量)）。

    /** 反馈事件 topic（分区键 {taskId}，分区数 3）。 */
    public static final String TOPIC_FEEDBACK = "TOPIC_FEEDBACK";

    /** 通知事件 topic（分区键 {channel}，分区数 3）。 */
    public static final String TOPIC_NOTIFY = "TOPIC_NOTIFY";

    /** 评审链路死信队列 topic（无分区键，分区数 3）。 */
    public static final String TOPIC_REVIEW_DLQ = "TOPIC_REVIEW_DLQ";

    /**
     * 兜底默认 topic 基名：仓库未配置 {@code repo.mq_topic} 时走此值。
     *
     * <p>这是「不阻塞 MVS」的降级支点（设计说明书 §3.4.5 / §8.4）：
     * MVS 与 v0.2 期间所有仓库都不配置 {@code mq_topic}，全部走
     * {@code aicr.default.request / .retry / .writeback}，系统行为与裁决 #12 之前完全一致。
     */
    public static final String DEFAULT_TOPIC_BASENAME = "aicr.default";

    // ── ③ topic 命名规范（worker 正则订阅的前提）──────────────────

    /**
     * topic 命名规范正则，与 {@code repo.mq_topic} 的 DB CHECK 约束保持一致
     * （设计说明书 §3.4.2：{@code ^[a-zA-Z0-9._-]{1,64}$}）。
     *
     * <p>长度 1~64 按「基名」收敛，留足事件后缀空间（Kafka topic 名最长 249 字符）。
     */
    public static final String TOPIC_NAME_PATTERN = "^[a-zA-Z0-9._-]{1,64}$";

    // ── ④ 分区键模板 ────────────────────────────────────────────

    /**
     * 仓库维度分区键模板：{platformConfigId}_{repoId}。
     *
     * <p>依据 LLD §10.1，保证同仓库的消息落入同一分区、串行处理。
     * 在仓库级 topic 内可进一步简化为 {@code {repoId}}（设计说明书 §3.4.7 回写项）。
     */
    public static final String PARTITION_KEY_REPO = "%s_%s";

    private KafkaConstant() {
    }
}
