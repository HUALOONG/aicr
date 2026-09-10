package com.joyintech.aicr.common.constant;

/**
 * 幂等键 / 锁键 / 缓存键模式 + 对应 TTL（秒）。
 *
 * <p>契约出处：编码规范 §7.2、LLD §2.3（分布式锁）、LLD §10.4（消费幂等）、
 * 裁决 #12 / 设计说明书 §3.4.4（仓库级 MQ topic 配置缓存）。
 * <p>使用方：aicr-webhook（生产端幂等）、aicr-worker（消费幂等）、aicr-engine（锁）、
 * aicr-service / aicr-web（配置缓存主动失效）。
 *
 * <p>占位符约定：{@code %s} 由使用方通过 {@link String#formatted} / {@link String#format} 填充。
 * <b>禁止</b>在本模块内做字符串拼装（A4 无副作用、A5 契约稳定）。
 */
public final class RedisKeyConstant {

    // ── ① 键模式 ────────────────────────────────────────────────

    /** Webhook 事件级幂等键：{eventId}。TTL {@link #TTL_WEBHOOK_EVENT_SECONDS}（24h）。 */
    public static final String WEBHOOK_EVENT_ID = "WEBHOOK_EVENT_ID:%s";

    /** Webhook 业务级去重键：{platformConfigId}_{repoId}_{mrId}。TTL {@link #TTL_BIZ_DEDUP_SECONDS}（60s）。 */
    public static final String WEBHOOK_BIZ_DEDUP = "%s_%s_%s";

    /** Kafka 消费幂等键：{msgId}。TTL {@link #TTL_MQ_CONSUMED_SECONDS}（7d）。 */
    public static final String MQ_CONSUMED = "aicr:mq:consumed:%s";

    /** 分析意图缓存键：{taskId}。TTL {@link #TTL_ANALYSIS_INTENT_SECONDS}（24h）。 */
    public static final String ANALYSIS_INTENT = "ANALYSIS_INTENT:%s";

    /** 仓库级分布式锁键：{repoId}。TTL {@link #TTL_LOCK_NONE}（无 TTL，依赖 watchdog 续期，LLD §2.3）。 */
    public static final String LOCK_REPO = "aicr:lock:repo:%s";

    /**
     * 仓库级 MQ topic 配置缓存键：{repoId}（裁决 #12 / 设计说明书 §3.4.4）。
     *
     * <p>由 aicr-web / aicr-service 侧修改仓库配置时主动 DEL；aicr-webhook 只读。
     * TTL {@link #TTL_CFG_REPO_MQ_TOPIC_SECONDS}（300s）。
     * 注意：未配置的仓库也须缓存「哨兵值」（如空串），否则形成缓存穿透。
     */
    public static final String CFG_REPO_MQ_TOPIC = "aicr:cfg:repo:mqltopic:%s";

    // ── ② TTL（单位：秒）────────────────────────────────────────

    /** Webhook 事件级幂等：24 小时。 */
    public static final long TTL_WEBHOOK_EVENT_SECONDS = 24 * 60 * 60L;

    /** Webhook 业务级去重：60 秒。 */
    public static final long TTL_BIZ_DEDUP_SECONDS = 60L;

    /** Kafka 消费幂等：7 天。 */
    public static final long TTL_MQ_CONSUMED_SECONDS = 7 * 24 * 60 * 60L;

    /** 分析意图缓存：24 小时。 */
    public static final long TTL_ANALYSIS_INTENT_SECONDS = 24 * 60 * 60L;

    /** 配置类缓存：5 分钟（短 TTL，允许分钟级不一致，设计说明书 §3.4.4）。 */
    public static final long TTL_CFG_REPO_MQ_TOPIC_SECONDS = 5 * 60L;

    /** 分布式锁「无 TTL」哨兵值：-1（显式表达不设过期，LLD §2.3）。 */
    public static final long TTL_LOCK_NONE = -1L;

    private RedisKeyConstant() {
    }
}
