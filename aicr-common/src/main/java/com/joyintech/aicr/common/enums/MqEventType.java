package com.joyintech.aicr.common.enums;

import com.joyintech.aicr.common.constant.KafkaConstant;

/**
 * Kafka 消息 {@code eventType} 字段枚举，共 5 个。
 *
 * <p>契约出处：LLD §10.2（消息通用结构 {@code {msgId, traceId, eventType, occurredAt, payload}}）、
 * LLD §10.3（事件类型与 Payload）。
 *
 * <p>使用方：aicr-webhook（生产）、aicr-worker（消费，按 eventType 分发）、aicr-web（重试/反馈）。
 * {@code aicr-webhook} 与 {@code aicr-worker} 互相禁止依赖（红线三），二者共享本枚举是
 * common 存在的硬需求之一（设计说明书 §1.3）。
 *
 * <p>裁决 #12 后新增 {@link #topicSuffix()} / {@link #repoScoped()} 语义，
 * 作为「事件后缀 → topic 派生」的单一事实来源（设计说明书 §3.4.3）：
 * <ul>
 *   <li>{@code REVIEW_REQUEST / REVIEW_RETRY / WRITEBACK}：按仓库派生，
 *       实际 topic = {@code repo.mq_topic + suffix}（未配置走 {@code aicr.default}）；</li>
 *   <li>{@code FEEDBACK / NOTIFY}：系统级全局 topic，不按仓库派生。</li>
 * </ul>
 */
public enum MqEventType {

    /** 评审请求（Topic {@code TOPIC_REVIEW_REQUEST}，分区键 {@code {platformConfigId}_{repoId}}）。 */
    REVIEW_REQUEST(KafkaConstant.SUFFIX_REVIEW_REQUEST, true),

    /** 人工重试（Topic {@code TOPIC_REVIEW_RETRY}，分区键同 REVIEW_REQUEST）。 */
    REVIEW_RETRY(KafkaConstant.SUFFIX_REVIEW_RETRY, true),

    /** 结果回写（Topic {@code TOPIC_WRITEBACK}，分区键同 REVIEW_REQUEST）。 */
    WRITEBACK(KafkaConstant.SUFFIX_WRITEBACK, true),

    /** 反馈闭环（系统级全局 topic {@code TOPIC_FEEDBACK}，分区键 {@code {taskId}}）。 */
    FEEDBACK(null, false),

    /** 通知发送（系统级全局 topic {@code TOPIC_NOTIFY}，分区键 {@code {channel}}）。 */
    NOTIFY(null, false),
    ;

    private final String topicSuffix;
    private final boolean repoScoped;

    MqEventType(String topicSuffix, boolean repoScoped) {
        this.topicSuffix = topicSuffix;
        this.repoScoped = repoScoped;
    }

    /** eventType 字面量（= {@link #name()}，与消息体 JSON 字段对齐）。 */
    public String type() {
        return name();
    }

    /**
     * 事件后缀；{@code null} 表示该事件使用系统级全局 topic，不按仓库派生。
     */
    public String topicSuffix() {
        return topicSuffix;
    }

    /** 是否按仓库派生 topic。 */
    public boolean repoScoped() {
        return repoScoped;
    }

    /**
     * 按字面量解析。
     *
     * @param type eventType 值（如 {@code "REVIEW_REQUEST"}）
     * @return 匹配的枚举；未知或 {@code null} 返回 {@code null}（不抛异常）
     */
    public static MqEventType of(String type) {
        if (type == null) {
            return null;
        }
        for (MqEventType value : values()) {
            if (value.name().equals(type)) {
                return value;
            }
        }
        return null;
    }
}
