/**
 * 工具类包（当前为空，仅占位）。
 *
 * <p>LLD §2.4.2 给 aicr-common 列了 {@code util} 子包，但按 §1.1 准入判据逐条筛查后，
 * <b>当前没有任何工具类满足准入</b>，因此刻意不造轮子，仅保留 {@code package-info.java}
 * （Git 不跟踪空目录，且承载准入判据的 Javadoc）。首个真正满足准入的工具类落地时再补。
 *
 * <p><b>准入判据（必须全部满足才可放入 {@code aicr-common.util}）</b>：
 * <ul>
 *   <li><b>A1 零框架依赖</b>：不得 import {@code org.springframework.*} / {@code org.springframework.boot.*}
 *       / {@code jakarta.*} / {@code org.mybatis.*} / {@code com.baomidou.*} /
 *       {@code org.slf4j.*} / {@code com.fasterxml.jackson.*}；hutool 仅限 {@code cn.hutool.core..}（裁决 #13）。</li>
 *   <li><b>A2 跨模块复用</b>：被 ≥2 个 Maven 模块使用，或由 HLD/LLD/openapi/编码规范明确为全局契约。</li>
 *   <li><b>A3 无业务语义</b>：只承载"字典/码表/结构/纯函数"，不含业务规则判断。</li>
 *   <li><b>A4 无副作用</b>：无 I/O、无网络、无文件、无线程、无静态可变状态、无配置读取。</li>
 *   <li><b>A5 契约稳定</b>：取值来自 openapi / DBD / 编码规范；不得在实现中发明编码。</li>
 * </ul>
 *
 * <p><b>已明确淘汰的候选</b>（及归属）：
 * <ul>
 *   <li>{@code IdUtil} → JDK {@code UUID.randomUUID()} 一行，A5 无需（不建）。</li>
 *   <li>{@code JsonUtil} → 违反 A1（jackson），归 {@code aicr-base}（复用 Spring 容器管理的 ObjectMapper）。</li>
 *   <li>{@code Assert} / {@code BizAssert} → 会造成 common→base 反向依赖，归 {@code aicr-base}。</li>
 *   <li>{@code MaskUtil} → LLD §2.4.3 已归 {@code aicr-security.crypto}。</li>
 *   <li>{@code TimeUtil} → 时间格式由 Jackson 全局配置统一，A5/A2 不成立。</li>
 *   <li>{@code TreeUtil} → 仅 {@code service.perm} 使用（准出 B5 单模块），留在 service。</li>
 * </ul>
 *
 * @author aicr-common
 */
package com.joyintech.aicr.common.util;
