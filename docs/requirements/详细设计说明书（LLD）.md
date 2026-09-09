# AI 驱动代码评审与测试生成平台 — 软件详细设计说明书（LLD）

---

| 版本 | 日期       | 变更内容                                 | 变更人 |
|:-----|:-----------|:-----------------------------------------|:-------|
| v1.0 | 2026-09-08 | 初始版本（依据 SRS v1.0、HLD v1.0 编制） | 王飞   |

---

## 1 引言

### 1.1 编写目的

本文档为《AI 驱动代码评审与测试生成平台
v1.0》的软件详细设计说明书（LLD），旨在将高层架构设计（HLD）与需求规格说明书（SRS）转化为具体的、可编码实现的工程细节。文档详细定义了系统各模块的内部结构、类/服务设计、数据库表结构、API
契约及状态流转逻辑，为后端开发、前端对接、测试用例编写提供唯一事实依据。

### 1.2 适用范围

本文档覆盖平台全部 7 大核心模块（评审概览、智能分析、规则配置、运维管理、权限管理、系统配置、核心引擎）的详细设计，适用于后端研发工程师、前端工程师、测试工程师及架构师。

> **范围约束（多租户）**：本期（v1.0）为 **单租户**设计，所有数据表不引入 `tenant_id` 隔离；二期若支持多租户，将通过可空
> `tenant_id` 列或 Schema 隔离扩展，当前表结构预留该扩展点（不破坏现有字段）。（引擎组 C 已推荐）

### 1.3 术语定义

| 术语           | 定义                                                        |
|:---------------|:------------------------------------------------------------|
| MR/PR          | Merge Request / Pull Request，代码合并请求                  |
| Diff           | 代码变更差异                                                |
| AST            | Abstract Syntax Tree，抽象语法树                            |
| LLM            | Large Language Model，大语言模型                            |
| Webhook        | 代码托管平台的事件回调机制                                  |
| RPC            | Remote Procedure Call，本文指 HTTP+JSON 的 RPC 风格接口     |
| RBAC           | Role-Based Access Control，基于角色的访问控制               |
| Idempotency    | 幂等性，同一请求多次执行产生的效果与执行一次相同            |
| Diff Context   | Diff 上下文，变更行前后的无关代码行，用于提供代码阅读上下文 |
| Inline Comment | 代码托管平台支持的具体代码行级别的评论                      |
| Token          | LLM 调用时的输入/输出 token 数量                            |
| JSONB          | PostgreSQL 的 JSON 二进制存储格式，支持索引                 |

### 1.4 参考资料

- IEEE 830 / ISO/IEC/IEEE 29148 软件需求规格说明标准
- OWASP Top 10 安全规范
- GB/T 8567-2006 计算机软件文档编制规范
- 《AI 驱动代码评审与测试生成平台 — 软件需求规格说明书（SRS）v1.0》
- 《AI 驱动代码评审与测试生成平台 — 概要设计说明书（HLD）v1.0》
- 《AI 驱动代码评审与测试生成平台 — Prompt（指令）设计说明书 v1.0》（指令模板、变量契约、输出 Schema、Token 预算与降级变体的唯一依据）

---

## 2 系统架构细化

### 2.1 技术栈版本与关键依赖

| 分类          | 技术/组件                                                          | 版本                 | 关键依赖/说明                            |
|:--------------|:-------------------------------------------------------------------|:---------------------|:-----------------------------------------|
| 后端框架      | Spring Boot                                                        | 4.1.x                | 核心业务逻辑、RPC 接口                   |
| 前端框架      | Vue 3 + TypeScript + Vite+ + Tailwind CSS v4 + shadcn-vue(Reka UI) | Latest               | 管理控制台（详见《前端详细设计说明书》） |
| 关系型数据库  | PostgreSQL                                                         | 18+                  | 核心业务数据存储、JSONB 支持             |
| 缓存/分布式锁 | Redis                                                              | 8.0+                 | 幂等校验、结果缓存、分布式锁             |
| 消息队列      | Kafka                                                              | 4.3+                 | Webhook 异步解耦、任务调度               |
| LLM 接入      | OpenAI Compatible API                                              | -                    | GPT-4o / DeepSeek / 通义千问             |
| 代码托管      | GitLab / GitHub / Gitea                                            | 13+ / Latest / 1.16+ | 外部集成                                 |

### 2.2 核心设计模式落地

- **策略模式 (Strategy Pattern)**：WebhookParser 接口，实现
  GitLabWebhookParser、GitHubWebhookParser、GiteaWebhookParser。根据请求 Header 或 Payload 特征动态路由至对应解析器。
- **适配器模式 (Adapter Pattern)**：LLMClient 接口，实现 OpenAILLMAdapter、DeepSeekLLMAdapter 等。屏蔽不同厂商 API
  差异，统一输入输出格式。
- **工厂模式 (Factory Pattern)**：ReviewRuleFactory，根据规则类型（BUG/SECURITY 等）实例化对应的评审处理器。
- **流水线模式 (Pipeline Pattern)**：AnalysisPipeline，编排"意图分析 → 评审+测试并行生成"的三阶段执行逻辑。

### 2.3 逻辑架构细化

系统采用五层架构：

1. **展示层**：Vue 3 + TypeScript + Vite+ + Tailwind CSS v4 + shadcn-vue(Reka UI) 管理控制台，包含评审概览、智能分析、规则配置、
   运维管理、权限管理、系统配置 6 大功能域。
2. **接入层**：统一 RPC 路由 /api/v1/{module}/{action}，JWT 鉴权拦截器，Webhook 回调端点（独立鉴权，仅 HMAC 签名校验）。
3. **应用服务层**：Spring Boot 应用，按业务域划分 Service（DashboardService、ReviewTaskService、PromptService 等）。
4. **核心引擎层**：WebhookReceiver、AnalysisEngine、ResultWriter、FeedbackLoop 等核心组件，通过 Kafka 与外部解耦。
5. **存储与缓存层**：PostgreSQL 18+ 持久化存储，Redis 8.0+ 用于幂等校验、结果缓存、分布式锁。
    - **Redis 缓存与淘汰策略**：`maxmemory` 设为部署实例内存的 **70%**（如 4G 实例配 2.8G）；淘汰策略
      `maxmemory-policy = **volatile-lru**`（仅在**设置了 TTL 的键**中按 LRU 淘汰）。
        - 幂等键 `WEBHOOK_EVENT_ID:{eventId}`（TTL 24h）、消息幂等键 `aicr:mq:consumed:{msgId}`（TTL 7 天）、结果缓存 `ANALYSIS_INTENT:{commitId}`（TTL 24h）均带 TTL，参与淘汰；
        - **分布式锁 `aicr:lock:repo:{repoId}` 不设 TTL**，在 `volatile-lru` 下永不被淘汰；释放依赖业务显式解锁与 Redisson watchdog 续期（进程崩溃时由 watchdog 超时释放），避免锁键被淘汰导致的并发穿透。

> 修订说明：原策略写作 `allkeys-lru` 且称"分布式锁独立使用、不设淘汰"，但 `allkeys-lru` 会连无 TTL 的锁键一并淘汰，该组合不成立，故本期改为 `volatile-lru`。

外部集成层：代码托管平台（GitLab/GitHub/Gitea）出站接口、LLM 提供商出站接口、通知渠道（企微/钉钉/飞书/邮件）。

---

### 2.4 Maven 模块与包结构映射

本节将 HLD §2.4 的 9 个 Maven 工程模块，映射到 §3 的 7 大功能模块与具体 Java 包，作为编码落地的包级约束。

#### 2.4.1 功能模块 ↔ Maven 模块映射

| 功能模块（§3） | 主 Maven 模块                                            | 包（package）                                                                                                                                                    | 对应需求                       |
|:---------------|:---------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------|
| 评审概览       | `aicr-service` + `aicr-web`                              | `..service.dashboard` / `..web.controller.dashboard`                                                                                                             | FR-001~004                     |
| 智能分析       | `aicr-service` + `aicr-web` + `aicr-engine`（经 worker） | `..service.review` / `..web.controller.review` / `..engine.pipeline`·`..engine.writeback`·`..engine.feedback`                                                    | FR-005~007, FR-023~024, FR-026 |
| 规则配置       | `aicr-service` + `aicr-web`                              | `..service.rule` / `..web.controller.rule`                                                                                                                       | FR-008~011                     |
| 运维管理       | `aicr-service` + `aicr-web`                              | `..service.ops` / `..web.controller.ops`                                                                                                                         | FR-012~013                     |
| 权限管理       | `aicr-service` + `aicr-web` + `aicr-security`            | `..service.perm` / `..web.controller.perm` / `..security.rbac`                                                                                                   | FR-014~017                     |
| 系统配置       | `aicr-service` + `aicr-web`                              | `..service.setting` / `..web.controller.setting`                                                                                                                 | FR-018~021                     |
| 核心引擎       | `aicr-engine` + `aicr-webhook` + `aicr-worker`           | `..webhook.controller`·`..webhook.parser`·`..webhook.service` / `..engine.pipeline`·`..engine.writeback`·`..engine.largechange`·`..engine.feedback` / `..worker` | FR-022~026                     |

> 说明：FR-023/024/026 同时出现在"智能分析"与"核心引擎"两行，因引擎能力（`engine.pipeline`/`writeback`/`feedback`）经
> `worker` 被智能分析模块编排调用，属同一业务链路的物理拆分，并非需求重复。

> 说明：`review/trigger`、`review/retry`、`webhook/retry` 在 `service` 中仅 **发送 Kafka 消息**，不直接调用 `engine` 内部类；
> `engine` 由 `worker` 进程消费执行（HLD §2.4.3 红线二）。

#### 2.4.2 各模块包结构约定

- **`aicr-common`**：`com.joyintech.aicr.common.{constant,enums,util,result,exception}`
- **`aicr-api`**：`com.joyintech.aicr.api.{dto,constant,facade}`（`facade` 为内部接口声明：`CostRecorder`、`QuotaGuard`、
  `NotifySender`、`CodePlatformClient`，由 `service`/`engine` 实现）
- **`aicr-security`**：`com.joyintech.aicr.security.{auth,crypto,rbac,webhook}`（`auth` 含 JWT 拦截器；`webhook` 含
  `SecurityUtil.verify` 与 IP 黑名单；`rbac` 定义 `DataScopeResolver` SPI）
- **`aicr-base`**：`com.joyintech.aicr.base.{repository,mq,config,exception,redis}`（`repository` 含 Mapper/Repository；
  `exception` 含 `GlobalExceptionHandler`、`BusinessException` 等，见 §8.1）
- **`aicr-service`**：`com.joyintech.aicr.service.{dashboard,review,rule,ops,perm,setting}`（每包对应一个功能域 Service；
  `review` 包负责发 Kafka 触发消息）
- **`aicr-engine`**：
    - `..engine.pipeline`：`AnalysisEngineService`、`LLMChainExecutor`（FR-023 三阶段）
    - `..engine.writeback`：`ResultWriteBackService`（FR-024）
    - `..engine.largechange`：`LargeChangeHandler`（FR-025）
    - `..engine.feedback`：`FeedbackLoopService`（FR-026）
    - `..engine.integration`（临时）：`LLMClient`、`CodePlatformClient`、`NotifySender` 适配器（二期抽 `aicr-integration`，见 HLD §2.4.7）
- **`aicr-web`**：`com.joyintech.aicr.web.controller.{dashboard,review,rule,ops,perm,setting,auth}`，装配
  `service+security+api+base`
- **`aicr-webhook`**：`com.joyintech.aicr.webhook.controller.WebhookController` + `..webhook.parser`（`WebhookParseStrategy`）+ `..webhook.service`（幂等/建任务/入队），装配 `base+security+api+common`
- **`aicr-worker`**：`com.joyintech.aicr.worker.WorkerApplication` + `@KafkaListener` 转发至 `engine`，装配 `engine+service+base+security+api+common`

#### 2.4.3 关键类模块归宿（与 §3 对齐）

| LLD §3 类名                                           | 归属模块                                | 说明                             |
|:------------------------------------------------------|:----------------------------------------|:---------------------------------|
| `WebhookController`、`WebhookParseStrategy`（§3.7.1） | `webhook.controller` / `webhook.parser` | 二者均在 `webhook` 模块内        |
| `AnalysisEngineService`、`LLMChainExecutor`（§3.7.2） | `engine.pipeline`                       | 经 worker 驱动                   |
| `ResultWriteBackService`（§3.7.3）                    | `engine.writeback`                      | 经 worker 驱动                   |
| `LargeChangeHandler`（§3.7.4）                        | `engine.largechange`                    | 经 worker 驱动                   |
| `FeedbackLoopService`（§3.7.5）                       | `engine.feedback`                       | 经 worker 驱动                   |
| `SecurityUtil`（§3.7.1 签名校验）                     | `security.webhook`                      | 库提供，`webhook` 调用           |
| `SensitiveDataMasker`（§7.3）                         | `security.crypto`                       | 库提供，`engine` 发送 LLM 前调用 |
| 各 `*Service`（Dashboard/Review/…）                   | `service.*`                             | 见 §2.4.2                        |

## 3 核心模块详细设计

### 3.1 评审概览模块（Dashboard Module）

#### 3.1.1 概览总览（FR-001）

- **服务名**：DashboardService
- **接口**：POST /api/v1/dashboard/summary
- **输入**：时间范围（默认近 30 天）、仓库 ID（可选）、用户 ID（可选）
- **处理逻辑**：
    1. 根据数据权限过滤可见仓库
    2. 聚合查询 review_task 与 review_comment 表
    3. 计算综合评分公式：Score = 100 - (Blocker×10 + Critical×5 + Minor×1)，下限为 0
    4. 按时间维度 Group By 生成趋势数据
- **输出**：4 个指标卡片（评审总次数、发现问题总数、综合评分、测试覆盖率）+ 环比变化百分比
- **异常处理**：查询超时返回缓存数据（若存在）；无数据返回全零结构

#### 3.1.2 问题分布（FR-002）

- **服务名**：DashboardService
- **接口**：POST /api/v1/dashboard/issueDistribution
- **输入**：时间范围、仓库筛选条件
- **处理逻辑**：
    1. 按维度（BUG/PERFORMANCE/SECURITY/STYLE/READABILITY）聚合统计
    2. 按严重等级（BLOCKER/CRITICAL/MINOR）聚合统计
- **输出**：饼图数据（维度分布）+ 柱状图数据（严重等级分布）

#### 3.1.3 质量趋势（FR-003）

- **服务名**：DashboardService
- **接口**：POST /api/v1/dashboard/qualityTrend
- **输入**：时间粒度（日/周/月）、时间范围
- **处理逻辑**：按时间粒度 Group By，计算每日/周/月的综合评分和问题数量
- **输出**：折线图数据（综合评分趋势）+ 面积图数据（问题数量趋势）

#### 3.1.4 仓库分析（FR-004）

- **服务名**：DashboardService
- **接口**：POST /api/v1/dashboard/repoAnalysis
- **输入**：时间范围
- **处理逻辑**：按仓库聚合评审次数、问题数、综合评分、活跃度评分
- **输出**：仓库列表（热力图或列表形式）
- **权限控制**：ADMIN/PM/QA 可查看全量；DEVELOPER 仅查看自己有权限的仓库

---

### 3.2 智能分析模块（Review Analysis Module）

#### 3.2.1 任务列表（FR-005）

- **服务名**：ReviewTaskService
- **接口**：POST /api/v1/review/list
- **输入**：分页参数（page/size）、筛选条件（仓库、作者、状态、时间范围、关键字搜索）
- **处理逻辑**：
    1. 联表查询 review_task，应用数据权限过滤（**过滤规则由 §7.2 统一定义，`data_permission` 优先、内置角色策略兜底**）
    2. 未命中 `data_permission` 时按兜底策略：DEVELOPER 追加 `AND author_id = :currentUserId`
    3. 未命中 `data_permission` 时按兜底策略：PM/QA 追加 `AND dept_id IN (:userDeptTree)`（`dept_id` 为任务创建时从 `sys_user.dept_id`
       冗余的提交者部门，见 §4.1；`DEPT_AND_SUB` 语义为当前部门及全部下级部门）
    4. 默认按触发时间倒序排列，每页 20 条
- **输出**：分页表格（任务编号、仓库名称、MR/PR 标题、提交者、触发时间、状态、综合评分、是否包含测试用例）
- **状态映射**：
    - 0 待处理 → RECEIVED、PARSING、QUEUED
    - 1 处理中 → ANALYZING、GENERATING_TEST、WRITING_BACK、RETRYING、TIMEOUT
    - 2 已完成 → COMPLETED、PARTIAL_SUCCESS、DEGRADED
    - 3 失败 → FAILED
    - 4 已取消 → CANCELLED、SKIPPED_QUOTA

#### 3.2.2 分析报告（FR-006）

- **服务名**：ReviewAnalysisService
- **接口**：POST /api/v1/review/getReport
- **输入**：任务 ID
- **处理逻辑**：
    1. 根据 taskId 获取任务详情
    2. 分别组装变更解读、评审意见、测试用例三个 Tab 数据
- **Tab 1 - 变更解读**：
    - 输出：变更摘要、影响文件列表、隐含需求点列表、风险点列表
- **Tab 2 - 评审意见**：
    - 输出：Diff 视图 + 评审意见列表（问题描述、所属维度、严重等级、修复建议、关联代码行号）
    - QA 专属操作：可对每条评审意见执行"确认有效"/"标记误报"操作
- **Tab 3 - 测试用例**：
    - 输出：测试用例列表（用例名称、覆盖场景、前置条件、测试步骤、预期结果、关联代码行）
    - QA 专属操作：可手动补充测试用例、标注验证状态（待验证/已通过/已失败）
- **权限控制**：ADMIN/PM/QA 可查看全部报告；DEVELOPER 仅查看自己的报告

#### 3.2.3 变更追溯（FR-007）

- **服务名**：ReviewAnalysisService
- **接口**：POST /api/v1/review/getTrace
- **输入**：任务 ID
- **处理逻辑**：基于 review_task 关联的元数据构建图谱节点与边
- **输出**：关联关系图谱（节点：变更文件/需求点/评审问题/测试用例；边：关联关系）

---

### 3.3 规则配置模块（Rule Config Module）

#### 3.3.1 指令管理（FR-008）

- **服务名**：PromptService
- **接口**：POST /api/v1/prompt/list、getById、create、update、delete、getVersions、rollback、evaluate
- **输入**：模板对象（分类、内容）
- **处理逻辑**：
    1. 保存时自动生成新版本号，存入 prompt_version 表
    2. 变量替换逻辑：使用正则 {{ (.*?)}} 匹配占位符
    3. 预置变量：{{diff_content}}、{{file_path}}、{{language}}、{{rule_description}}
    4. 引用变量：{{ref:template_code}} → 递归查询 prompt_template 表
    5. 支持版本对比与回滚
- **输出**：操作结果、版本历史列表
- **异常处理**：模板语法错误抛出 40001

#### 3.3.2 评审规则（FR-009）

- **服务名**：RuleService
- **接口**：POST /api/v1/rule/list、getById、create、update、delete、toggleStatus
- **输入**：规则对象（所属维度、严重等级、关联指令模板）
- **处理逻辑**：
    1. 按维度（BUG/PERFORMANCE/SECURITY/STYLE/READABILITY）配置评审规则
    2. 每条规则关联指令模板
    3. 系统内置规则禁止删除
- **异常处理**：内置规则删除抛出 40301

#### 3.3.3 测试策略（FR-010）

- **服务名**：TestStrategyService
- **接口**：POST /api/v1/testStrategy/list、create、update、delete、suggest
- **输入**：策略对象（策略名称、目标框架、场景偏好 JSONB、命名规范、代码风格模板、指令模板、是否全局默认）
- **处理逻辑**：
    1. 校验 JSONB 格式（scenario_preference 为场景偏好数组）
    2. 支持多策略配置，通过 `is_default = 1` 标记全局默认策略
    3. 未显式指定策略时选用全局默认策略
- **输出**：操作结果

#### 3.3.4 规则模板（FR-011）

- **服务名**：TemplateService
- **接口**：`POST /api/v1/template/list`、`apply`、`create`、`update`、`delete`（共 5 个，与 §5.4.4 一致）
- **处理逻辑**：预置常用评审规则模板，支持一键导入；启用时按规则编号批量写入 `review_rule`，**已存在的同编号规则跳过**（SRS FR-011）

---

### 3.4 运维管理模块（Ops Module）

#### 3.4.1 系统监控（FR-012）

- **服务名**：MonitorService
- **接口**：POST /api/v1/monitor/metrics、/monitor/health
- **输入**：监控指标类型
- **处理逻辑**：
    1. 实时读取 Micrometer 指标（QPS、延迟、成功率）
    2. 读取 Redis 队列积压情况
    3. 读取 cost_consumption 表统计 Token 消耗
- **输出**：系统运行指标仪表盘数据

#### 3.4.2 审计日志（FR-013）

- **服务名**：AuditService
- **接口**：POST /api/v1/audit/list（导出：POST /api/v1/audit/export）
- **输入**：查询条件（操作人、类型、时间范围）
- **处理逻辑**：查询 audit_log 表，保留期由系统配置项 `audit.retention.days` 控制， **默认 180 天**（可配置；默认取值即满足
  SRS FR-013 与等保要求），满足等保/行业差异；超期由定时任务归档至冷存后清理。（引擎组 C 已推荐）
- **输出**：分页审计日志列表

---

### 3.5 权限管理模块（Permission Module）

#### 3.5.1 用户管理（FR-014）

- **服务名**：UserService
- **接口**：POST /api/v1/user/list、getById、create、update、delete、resetPassword、assignRoles、sync
- **处理逻辑**：基于 RBAC 的用户 CRUD 操作

#### 3.5.2 角色管理（FR-015）

- **服务名**：RoleService
- **接口**：POST /api/v1/role/list、getById、create、update、delete、getPermissions、assignPermissions
- **处理逻辑**：基于 RBAC 的角色 CRUD 与权限分配

#### 3.5.3 部门管理（FR-016）

- **服务名**：DeptService
- **接口**：POST /api/v1/dept/tree、getById、create、update、delete
- **处理逻辑**：部门组织架构管理（树形结构）

#### 3.5.4 数据权限（FR-017）

- **服务名**：DataPermissionService
- **接口**：POST /api/v1/dataPermission/list、create、update、delete
- **处理逻辑**：配置数据权限（主体 ROLE/DEPT × 资源 REVIEW_TASK/DASHBOARD/RULE × 范围 ALL/DEPT_AND_SUB/SELF）

---

### 3.6 系统配置模块（System Setting Module）

#### 3.6.1 平台接入（FR-018）

- **服务名**：PlatformService
- **接口**：POST /api/v1/platform/list、create、update、delete、testConnection
- **处理逻辑**：配置代码托管平台接入（GitLab/GitHub/Gitea），Access Token 与 Webhook Secret 加密存储（AES-256）

#### 3.6.2 模型配置（FR-019）

- **服务名**：LlmModelService
- **接口**：POST /api/v1/model/list、create、update、delete、testConnection
- **处理逻辑**：
    1. 配置 LLM 模型（提供商、模型、API 地址/Key）
    2. API Key 使用 AES-256 加密存储
    3. 配置温度、max_tokens、超时（默认 60s）、单价（input/output price）、适用场景（REVIEW/TEST_GEN）

#### 3.6.3 通知设置（FR-020）

- **服务名**：NotificationService
- **接口**：POST /api/v1/notification/list、create、update、delete、testSend
- **处理逻辑**：配置通知渠道（企微/钉钉/飞书/邮件）

#### 3.6.4 成本管理（FR-021）

- **服务名**：CostService
- **接口**：POST /api/v1/cost/quotaList、getQuota、createQuota、updateQuota、deleteQuota、consumption、quotaStatus
- **处理逻辑**：
    1. 实时计费：每次 LLM 调用后按 Input/Output Token 及单价更新消耗
    2. 阈值告警：达 80%/90% 配额时告警
    3. 熔断机制：达 100% 配额暂停该范围 AI 评审，任务置 SKIPPED_QUOTA
    4. 配额重置：支持按月/周自动重置或手动调整
    5. **配额状态枚举（`cost/quotaStatus` 输出口径，与 SRS FR-021、前端 §9.7 一致）**：`NORMAL`（< 80%）/ `WARN`（≥ 80%，触发 BR-COST-02 告警）/ `CRITICAL`（≥ 90%，触发 BR-COST-02 告警）/ `BLOCKED`（= 100%，触发 BR-COST-03 熔断）

---

### 3.7 核心引擎模块（Core Engine Module）

#### 3.7.1 Webhook 接收与解析服务（FR-022）

- **类名**：WebhookController、WebhookParseStrategy
- **接口**：POST /api/v1/webhook/receive
- **输入**：HTTP Header、JSON Body
- **处理逻辑**：
    1. **签名校验**：根据 X-Gitlab-Token 或 X-Hub-Signature-256 调用 SecurityUtil.verify ()
    2. **事件过滤与处理**（对齐 SRS FR-022 事件类型表与 BR-W-01）：
        - `merge_request(open)` / `pull_request(opened)`：创建评审任务；
        - `merge_request(update)` / `pull_request(synchronize)`：**取消该 MR 的旧任务 + 创建新任务**（同一事务，见下方 §3b）；
        - `pull_request(closed)`（含合并）：取消该 MR 下处于未终态的任务（`CANCELLED`）；
        - `merge_request(merge)`：忽略，不触发评审，返回 200 OK；
        - 未列出的事件类型：返回 200 OK 并忽略，仅落计数指标与 DEBUG 级摘要，不记录 Payload 正文（BR-W-01 日志口径）。
    3. **幂等处理（两级）**：
        - **3a. 事件级（BR-W-07）**：Key `WEBHOOK_EVENT_ID:{eventId}`（GitLab `X-Gitlab-Event-UUID` / GitHub `X-GitHub-Delivery`；**Gitea 事件幂等键列入二期**），Redis SETNX，TTL 24h；命中直接返回 200 OK（`data.status=duplicate`）。
        - **3b. 业务级（BR-W-08）**：Key `{platformConfigId}_{repoId}_{mrId}`，窗口 60 秒；窗口内同一 MR 的旧任务置 `CANCELLED` 与新任务创建必须在**同一数据库事务**内完成；若旧任务已进入"处理中"（主状态 1），则**不取消旧任务**，直接丢弃新请求（返回 200 OK），避免中断正在进行的 LLM 调用。
    4. **任务创建**：
        - 生成 ReviewTask 实体，初始状态 RECEIVED
        - 发送消息到 Kafka Topic: TOPIC_REVIEW_REQUEST
    5. **响应策略**：
        - 处理成功 → 200 OK
        - 签名错误 → 401
        - 非目标事件 → 200 OK
        - 重复请求 → 200 OK
        - 参数错误 → 400
        - 系统繁忙 → 503
- **性能要求**：200ms 内完成接收/校验/入队
- **Webhook 超时与重试（引擎组 B 已推荐）**：
    - 接收/校验/入队以 **200ms** 为性能目标（见上「性能要求」及 BR-W-02）； **5s** 仅作为超过托管平台自身重试阈值（GitLab/GitHub
      默认约 10s）前的熔断上限——一旦异常/过载无法及时响应，尽快返回 **503** 交由托管平台按其自身策略重试，接收线程
      **不在本地重试**（重试依赖 Kafka/MQ，见 §2.3）。
    - 重试次数/间隔 **默认沿用托管平台（GitLab/GitHub/Gitea）默认值**，本期不在 `platform_config`
      （FR-018）开放重试参数（避免与平台侧冲突），如需自定义留作二期可选配置。
    - 与防重放/IP 黑名单（**BR-W-06 / HLD §7.2**，实现位于 `security.webhook` 包）协同：签名失败返回 401 不重试，仅 503 触发平台重试；GitHub/Gitea 另需校验 ±300s 时间窗（SRS §4.2）。

#### 3.7.2 智能分析引擎（FR-023）

- **类名**：AnalysisEngineService、LLMChainExecutor、PromptRenderEngine
- **指令来源**：全部指令模板、变量契约、输出 Schema、Token 预算与降级变体以《Prompt（指令）设计说明书 v1.0》为唯一依据（本章仅描述流程编排）
- **核心流程（三阶段流水线）**：

  **阶段一：变更意图分析**
    - 输入：Diff Context + prompt_template (Type: SYSTEM/REVIEW)
    - 输出：JSON { "summary": "...", "risk_points": [...] }
    - 落库：`review_change_analysis`
    - 缓存：Redis Key ANALYSIS_INTENT:{commitId}，TTL 24h
    - 失败处理：阶段一失败 → 任务 `ANALYZING → FAILED`，不进入阶段二/三（`PARSING → FAILED` 仅用于 Diff/AST 解析失败，见 §6.2.1）

  **阶段二：评审意见生成（与阶段三并行）**
    - 逻辑： **按维度分桶批量**——取启用规则按 `dimension` 分桶（最多 5 个），每个维度一次 LLM 调用；各维度调用并发执行
    - 修正说明：原"遍历 review_rule 逐条调用"改为分桶批量，原因见《Prompt 设计说明书》§4.2.1（逐条调用导致调用次数随规则数线性增长，无法守住
      P95 ≤300s）；配置项 `aicr.engine.review-mode=DIMENSION_BATCH|RULE_SINGLE`，默认 `DIMENSION_BATCH`
    - 单维度失败 → 该维度独立重试；全部失败 → `ANALYZING → FAILED`；部分失败 → 继续并按已成功维度产出
    - 降级策略：若 Token > 8000，触发 StreamlinedStrategy（仅核心文件，见《Prompt 设计说明书》§5.5）

  **阶段三：测试用例生成（与阶段二并行，可插拔）**
    - 逻辑：根据 test_strategy 调用 LLM
    - 输出：List<Testcase>
    - 可独立关闭，关闭后跳过阶段三
    - 失败处理：阶段三失败不影响评审结果，任务 `GENERATING_TEST → PARTIAL_SUCCESS`

- **关键规则**：
    - Token 预算熔断（默认 8000，超出截断或降级）
    - **Token 预算分配（整任务口径，上限 8000 含阶段一）**：8000 中先预留阶段一（变更意图分析）**≤ 1200**（输入 + 输出）；剩余
      **6800** 在阶段二（评审意见）与阶段三（测试用例）间按 **60% / 40%** 拆分（阶段二 **4080** / 阶段三 **2720**）；若阶段三（测试生成）被关闭，阶段二可使用全部剩余额度（8000 − 阶段一实际消耗）；总预算超限优先触发 §3.7.4 的精简/速览降级（联动备用模型
      Prompt 精简）。口径细节与 Prompt 文本优先级见《Prompt（指令）设计说明书》§5.1。
    - **降级判定对象**：§3.7.4 的降级阈值（< 4k / 4k~8k / > 8k）按 **整任务输入 Token 估算（含阶段一）** 判定；三个阶段共享同一判定结果，不按单阶段预算重复判定。
    - 结果缓存（同一 commit 变更意图缓存 24h）
    - 降级策略（主模型不可用切换备用模型，并标记 `DEGRADED`）
        - **备用模型 Prompt 精简（引擎组 A 已确认）**：降级时启用精简 Prompt——保留 SECURITY / BUG / **PERFORMANCE** 核心规则，剔除
          Few-shot 示例、STYLE / READABILITY 维度与冗余上下文，以降低 Token 消耗；精简后仍超预算则按速览模式（仅安全检查）处理。
    - 并发控制（同仓库串行、跨仓库并行，分布式锁）
    - 超时重试（默认 60s，最多 3 次，间隔 5s/30s/120s）

#### 3.7.3 结果回写服务（FR-024）

- **类名**：ResultWriteBackService
- **处理逻辑**：
    1. 获取 review_comment 列表
    2. 调用 CodePlatformClient（GitLab/GitHub Adapter）
    3. **行级评论**：调用 POST /discussions（GitLab）或 POST /comments（GitHub），需计算 position（line_no）
    4. **汇总评论**：调用 POST /notes，包含测试用例摘要
    5. 回写失败标记 PARTIAL_SUCCESS，支持人工重试

#### 3.7.4 大变更处理策略（FR-025）

- **类名**：LargeChangeHandler
- **硬性阈值拦截**：
    - 单文件 >1000 行 → **本期直接跳过评审**（分片模式 SHARDED 列为二期，见 HLD §4.5）：写入日志（含仓库名、MR ID、文件路径、行数），并在
      MR 汇总评论中提示"文件变更过大，建议拆分"（与 SRS FR-025 一致）。
    - 单次 MR 总变更 >2000 行 → 触发精简模式（仅评审前 10 个核心文件）
    - 文件数 >20 → 触发目录级评审
    - Payload >5MB → 拒绝请求（HTTP 413）
- **智能精简**：上下文折叠（默认保留变更行前后各 3-5 行上下文）、非代码内容过滤、主语言识别
- **降级模式**（触发条件中的 Token = **整任务输入 Token 估算**，含阶段一；估算方法见《Prompt（指令）设计说明书》§5.2）：

  | 模式     | 触发条件        | 行为                                              |
      |:---------|:----------------|:--------------------------------------------------|
  | 标准模式 | Token < 4k      | 全量分析                                          |
  | 精简模式 | 4k ≤ Token ≤ 8k | 变更意图 + 关键逻辑评审，跳过风格检查，不生成测试 |
  | 速览模式 | Token > 8k      | 仅安全检查（SQL注入/硬编码密码/空指针）           |
  | 分片模式 | 单文件极大      | 多 Chunk 并行评审（建议二期实现）                 |

#### 3.7.5 反馈闭环与模型优化（FR-026）

- **类名**：FeedbackLoopService
- **处理逻辑**：
    1. **误报特征库**：标记误报时提取 AST 特征与语义向量入库
    2. **动态上下文注入**：相似度 >85% 时注入负面约束示例
    3. **确认样本库**：确认有效且提交修复后存入高质量修复样本库供 Few-shot
    4. **效果评估报表**：每周生成模型效果周报
- **接口**：POST /api/v1/feedback/report

---

## 4 数据库详细设计

### 4.0 表清单总览（共 26 张）

| 分类       | 表名                                                                                                                     |
|:-----------|:-------------------------------------------------------------------------------------------------------------------------|
| 权限与组织 | `sys_user`、`sys_role`、`sys_user_role`、`sys_dept`、`sys_menu`、`sys_role_menu`、`sys_user_platform`、`data_permission` |
| 项目与仓库 | `project`、`repo`                                                                                                        |
| 评审核心   | `review_task`、`review_change_analysis`、`review_comment`、`test_case`                                                   |
| 规则与指令 | `prompt_template`、`prompt_version`、`review_rule`、`test_strategy`                                                      |
| 接入与配置 | `platform_config`、`model_config`、`notification_config`                                                                 |
| 运维与成本 | `audit_log`、`cost_quota`、`cost_consumption`                                                                            |
| 反馈闭环   | `false_positive_feature`、`fix_sample`                                                                                   |

### 4.1 评审任务表（review_task）

| 字段名             | 类型        | 长度 | 允许空 | 默认值 | 说明                                                                                                                                                                                 | 索引        |
|:-------------------|:------------|:-----|:-------|:-------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------|
| id                 | BIGINT      | 20   | NO     | -      | 主键                                                                                                                                                                                 | PK          |
| task_no            | VARCHAR     | 32   | NO     | -      | 任务编号 (TR-20260908-001)                                                                                                                                                           | UK          |
| repo_name          | VARCHAR     | 128  | NO     | -      | 仓库名（冗余展示用）                                                                                                                                                                 | IDX_REPO    |
| repo_id            | BIGINT      | 20   | YES    | -      | 关联仓库ID（repo）；未匹配到已登记仓库时为空                                                                                                                                         | IDX_REPO_ID |
| platform_config_id | BIGINT      | 20   | YES    | -      | 关联平台接入ID（platform_config），用于回写与 Diff 拉取                                                                                                                              | IDX_PF      |
| project_id         | BIGINT      | 20   | YES    | -      | 关联项目ID（project），成本归集与配额主体（BR-COST-01/03）                                                                                                                           | IDX_PROJECT |
| dept_id            | BIGINT      | 20   | YES    | -      | 提交者所属部门ID（创建时从 sys_user.dept_id 冗余），供数据权限过滤（§3.2.1）                                                                                                         | IDX_DEPT    |
| test_strategy_id   | BIGINT      | 20   | YES    | -      | 实际使用的测试策略ID（仓库绑定优先，未绑定取全局默认，FR-010）                                                                                                                       | -           |
| mr_id              | VARCHAR     | 64   | NO     | -      | MR/PR ID                                                                                                                                                                             | -           |
| mr_title           | VARCHAR     | 256  | YES    | -      | MR/PR 标题                                                                                                                                                                           | -           |
| branch             | VARCHAR     | 128  | YES    | -      | 分支名                                                                                                                                                                               | -           |
| commit_id          | VARCHAR     | 64   | YES    | -      | 提交 SHA                                                                                                                                                                             | -           |
| author_id          | BIGINT      | 20   | NO     | -      | 提交者用户ID（关联用户表）                                                                                                                                                           | -           |
| status             | SMALLINT    | 2    | NO     | 0      | 0-待处理,1-处理中,2-已完成,3-失败,4-已取消                                                                                                                                           | IDX_STATUS  |
| sub_status         | VARCHAR     | 32   | NO     | -      | 业务状态（细分），取值见 SRS §3.1：RECEIVED/PARSING/QUEUED/ANALYZING/GENERATING_TEST/WRITING_BACK/COMPLETED/PARTIAL_SUCCESS/FAILED/TIMEOUT/CANCELLED/RETRYING/DEGRADED/SKIPPED_QUOTA | -           |
| score              | INTEGER     | 11   | YES    | -      | 综合评分                                                                                                                                                                             | -           |
| has_test_cases     | BOOLEAN     | -    | YES    | false  | 是否包含测试用例                                                                                                                                                                     | -           |
| triggered_by       | SMALLINT    | 2    | NO     | 0      | 0-Webhook, 1-手动                                                                                                                                                                    | -           |
| fallback_mode      | VARCHAR     | 16   | YES    | -      | 降级模式 (STANDARD/STREAMLINED/QUICK_SCAN/SHARDED)                                                                                                                                   | -           |
| fallback_reason    | VARCHAR     | 256  | YES    | -      | 降级原因说明                                                                                                                                                                         | -           |
| error_msg          | TEXT        | -    | YES    | -      | 失败原因                                                                                                                                                                             | -           |
| created_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 触发时间                                                                                                                                                                             | IDX_CREATED |
| updated_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                                                                                                                                             | -           |
| finished_at        | TIMESTAMPTZ | -    | YES    | -      | 完成时间                                                                                                                                                                             | -           |

### 4.2 评审意见表（review_comment）

| 字段名         | 类型        | 长度 | 允许空 | 默认值 | 说明                                                                             | 索引     |
|:---------------|:------------|:-----|:-------|:-------|:---------------------------------------------------------------------------------|:---------|
| id             | BIGINT      | 20   | NO     | -      | 主键                                                                             | PK       |
| task_id        | BIGINT      | 20   | NO     | -      | 关联任务ID                                                                       | IDX_TASK |
| file_path      | VARCHAR     | 512  | NO     | -      | 文件路径                                                                         | -        |
| line_no        | INTEGER     | 11   | YES    | -      | 行号                                                                             | -        |
| dimension      | VARCHAR     | 32   | NO     | -      | BUG/PERFORMANCE/SECURITY/STYLE/READABILITY                                       | IDX_DIM  |
| severity       | VARCHAR     | 16   | NO     | -      | BLOCKER/CRITICAL/MINOR                                                           | IDX_SEV  |
| title          | VARCHAR     | 256  | NO     | -      | 问题标题                                                                         | -        |
| description    | TEXT        | -    | NO     | -      | 问题描述                                                                         | -        |
| suggestion     | TEXT        | -    | YES    | -      | 修复建议                                                                         | -        |
| rule_id        | BIGINT      | 20   | YES    | -      | 关联规则ID（外键→review_rule.id，可空：通用 LLM 分析产生的意见可不关联具体规则） | -        |
| confidence     | NUMERIC     | 3,2  | YES    | -      | LLM 输出置信度 0~1；<0.5 的输出在落库前被过滤，见《Prompt 设计说明书》§4.2.5     | -        |
| confirm_status | SMALLINT    | 2    | NO     | 0      | 0-待确认,1-有效,2-误报                                                           | -        |
| confirmed_by   | BIGINT      | 20   | YES    | -      | 确认人                                                                           | -        |
| confirmed_at   | TIMESTAMPTZ | -    | YES    | -      | 确认时间                                                                         | -        |
| created_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                                                         | -        |
| updated_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                                         | -        |

### 4.3 测试用例表（test_case）

| 字段名          | 类型        | 长度 | 允许空 | 默认值 | 说明                        | 索引     |
|:----------------|:------------|:-----|:-------|:-------|:----------------------------|:---------|
| id              | BIGINT      | 20   | NO     | -      | 主键                        | PK       |
| task_id         | BIGINT      | 20   | NO     | -      | 关联任务ID                  | IDX_TASK |
| case_name       | VARCHAR     | 256  | NO     | -      | 用例名称                    | -        |
| scenario        | VARCHAR     | 32   | NO     | -      | POSITIVE/BOUNDARY/EXCEPTION | IDX_SCN  |
| precondition    | TEXT        | -    | YES    | -      | 前置条件                    | -        |
| steps           | TEXT        | -    | NO     | -      | 测试步骤                    | -        |
| expected_result | TEXT        | -    | NO     | -      | 预期结果                    | -        |
| file_path       | VARCHAR     | 512  | YES    | -      | 关联文件路径                | -        |
| line_no         | INTEGER     | 11   | YES    | -      | 关联代码行号                | -        |
| source          | SMALLINT    | 2    | NO     | 0      | 0-AI, 1-手动                | -        |
| verify_status   | SMALLINT    | 2    | NO     | 0      | 0-待验证,1-已通过,2-已失败  | -        |
| verified_by     | BIGINT      | 20   | YES    | -      | 验证人                      | -        |
| verified_at     | TIMESTAMPTZ | -    | YES    | -      | 验证时间                    | -        |
| created_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                    | -        |
| updated_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                    | -        |

### 4.4 指令模板表（prompt_template）

| 字段名          | 类型        | 长度 | 允许空 | 默认值 | 说明                                                         | 索引    |
|:----------------|:------------|:-----|:-------|:-------|:-------------------------------------------------------------|:--------|
| id              | BIGINT      | 20   | NO     | -      | 主键                                                         | PK      |
| prompt_code     | VARCHAR     | 64   | NO     | -      | 指令编号，`{{ref:}}` 引用键，规范见《Prompt 设计说明书》§2.3 | UK      |
| prompt_name     | VARCHAR     | 128  | NO     | -      | 指令名称                                                     | -       |
| category        | VARCHAR     | 32   | NO     | -      | 分类：SYSTEM/REVIEW/TEST_GEN/COMMON                          | IDX_CAT |
| content         | TEXT        | -    | NO     | -      | 指令内容（支持变量占位符）                                   | -       |
| current_version | INTEGER     | 11   | NO     | 1      | 当前版本号                                                   | -       |
| status          | SMALLINT    | 2    | NO     | 1      | 启用状态：1-启用,0-停用                                      | -       |
| created_by      | BIGINT      | 20   | YES    | -      | 创建人                                                       | -       |
| created_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                                     | -       |
| updated_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                     | -       |

### 4.5 指令版本表（prompt_version）

| 字段名      | 类型        | 长度 | 允许空 | 默认值 | 说明             | 索引       |
|:------------|:------------|:-----|:-------|:-------|:-----------------|:-----------|
| id          | BIGINT      | 20   | NO     | -      | 主键             | PK         |
| prompt_id   | BIGINT      | 20   | NO     | -      | 关联指令ID       | IDX_PROMPT |
| version     | INTEGER     | 11   | NO     | -      | 版本号           | -          |
| content     | TEXT        | -    | NO     | -      | 该版本的指令内容 | -          |
| change_desc | VARCHAR     | 256  | YES    | -      | 变更说明         | -          |
| created_by  | BIGINT      | 20   | YES    | -      | 操作人           | -          |
| created_at  | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间         | -          |
| updated_at  | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间         | -          |

### 4.6 评审规则表（review_rule）

| 字段名          | 类型        | 长度 | 允许空 | 默认值 | 说明                                                 | 索引    |
|:----------------|:------------|:-----|:-------|:-------|:-----------------------------------------------------|:--------|
| id              | BIGINT      | 20   | NO     | -      | 主键                                                 | PK      |
| rule_code       | VARCHAR     | 32   | NO     | -      | 规则编号（UNIQUE）                                   | UK      |
| rule_name       | VARCHAR     | 128  | NO     | -      | 规则名称                                             | -       |
| dimension       | VARCHAR     | 32   | NO     | -      | 所属维度：BUG/PERFORMANCE/SECURITY/STYLE/READABILITY | IDX_DIM |
| description     | TEXT        | -    | NO     | -      | 规则描述                                             | -       |
| severity        | VARCHAR     | 16   | NO     | -      | 默认严重等级：BLOCKER/CRITICAL/MINOR                 | -       |
| prompt_template | TEXT        | -    | YES    | -      | 关联指令模板内容                                     | -       |
| is_system       | SMALLINT    | 2    | NO     | 0      | 是否系统内置：1-是,0-否                              | -       |
| status          | SMALLINT    | 2    | NO     | 1      | 启用状态：1-启用,0-停用                              | -       |
| created_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                             | -       |
| updated_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                             | -       |

### 4.7 测试策略表（test_strategy）

| 字段名              | 类型        | 长度 | 允许空 | 默认值 | 说明                                                   | 索引 |
|:--------------------|:------------|:-----|:-------|:-------|:-------------------------------------------------------|:-----|
| id                  | BIGINT      | 20   | NO     | -      | 主键                                                   | PK   |
| strategy_name       | VARCHAR     | 128  | NO     | -      | 策略名称                                               | -    |
| test_framework      | VARCHAR     | 32   | NO     | -      | 目标测试框架                                           | -    |
| scenario_preference | JSONB       | -    | YES    | -      | 覆盖场景偏好（JSON 数组：POSITIVE/BOUNDARY/EXCEPTION） | -    |
| naming_convention   | VARCHAR     | 256  | YES    | -      | 命名规范                                               | -    |
| code_style_template | TEXT        | -    | YES    | -      | 代码风格模板                                           | -    |
| prompt_template     | TEXT        | -    | YES    | -      | 关联指令模板内容                                       | -    |
| is_default          | SMALLINT    | 2    | NO     | 0      | 是否全局默认：1-是,0-否                                | -    |
| created_at          | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                               | -    |
| updated_at          | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                               | -    |

### 4.8 平台接入表（platform_config）

| 字段名         | 类型        | 长度 | 允许空 | 默认值 | 说明                                      | 索引     |
|:---------------|:------------|:-----|:-------|:-------|:------------------------------------------|:---------|
| id             | BIGINT      | 20   | NO     | -      | 主键                                      | PK       |
| platform_type  | VARCHAR     | 32   | NO     | -      | 平台类型：GITLAB/GITHUB/GITEA             | IDX_TYPE |
| api_url        | VARCHAR     | 256  | NO     | -      | API 地址                                  | -        |
| access_token   | TEXT        | -    | NO     | -      | Access Token（AES-256 加密，Base64 密文） | -        |
| webhook_secret | VARCHAR     | 128  | YES    | -      | Webhook Secret                            | -        |
| status         | SMALLINT    | 2    | NO     | 1      | 启用状态：1-启用,0-停用                   | -        |
| created_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                  | -        |
| updated_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                  | -        |

### 4.9 模型配置表（model_config）

| 字段名        | 类型        | 长度 | 允许空 | 默认值 | 说明                                                              | 索引    |
|:--------------|:------------|:-----|:-------|:-------|:------------------------------------------------------------------|:--------|
| id            | BIGINT      | 20   | NO     | -      | 主键                                                              | PK      |
| provider      | VARCHAR     | 64   | NO     | -      | 提供商（OpenAI/DeepSeek/通义千问）                                | IDX_PRV |
| model_name    | VARCHAR     | 64   | NO     | -      | 模型名称                                                          | -       |
| api_url       | VARCHAR     | 256  | NO     | -      | API 地址                                                          | -       |
| api_key       | TEXT        | -    | NO     | -      | API Key（AES-256 加密，Base64 密文）                              | -       |
| temperature   | NUMERIC     | 3,2  | YES    | 0.3    | 温度参数                                                          | -       |
| max_tokens    | INTEGER     | 11   | YES    | 4096   | 最大 Token 数                                                     | -       |
| timeout       | INTEGER     | 11   | YES    | 60     | 超时时间（秒）                                                    | -       |
| input_price   | NUMERIC     | 12,4 | YES    | 0      | 输入单价（每千 Token，BR-COST-01）                                | -       |
| output_price  | NUMERIC     | 12,4 | YES    | 0      | 输出单价（每千 Token，BR-COST-01）                                | -       |
| scene         | VARCHAR     | 32   | NO     | -      | 适用场景：REVIEW/TEST_GEN                                         | -       |
| data_training | SMALLINT    | 2    | NO     | 0      | 数据训练标识：0-承诺不用于训练,1-可能用于训练（SRS 4.2 隐私合规） | -       |
| is_backup     | SMALLINT    | 2    | NO     | 0      | 是否备用模型：0-主模型,1-备用模型（BR-06 降级切换）               | -       |
| status        | SMALLINT    | 2    | NO     | 1      | 启用状态：1-启用,0-停用                                           | -       |
| created_at    | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                                          | -       |
| updated_at    | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                          | -       |

### 4.10 数据权限表（data_permission）

| 字段名        | 类型        | 长度 | 允许空 | 默认值 | 说明                                 | 索引        |
|:--------------|:------------|:-----|:-------|:-------|:-------------------------------------|:------------|
| id            | BIGINT      | 20   | NO     | -      | 主键                                 | PK          |
| subject_type  | VARCHAR     | 16   | NO     | -      | 主体类型：ROLE/DEPT                  | -           |
| subject_id    | BIGINT      | 20   | NO     | -      | 主体ID（角色ID 或部门ID）            | IDX_SUBJECT |
| resource_type | VARCHAR     | 32   | NO     | -      | 资源类型：REVIEW_TASK/DASHBOARD/RULE | -           |
| scope         | VARCHAR     | 16   | NO     | -      | 可见范围：ALL/DEPT_AND_SUB/SELF      | -           |
| created_at    | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                             | -           |
| updated_at    | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                             | -           |

### 4.11 成本配额表（cost_quota）

| 字段名         | 类型        | 长度 | 允许空 | 默认值 | 说明                                          | 索引        |
|:---------------|:------------|:-----|:-------|:-------|:----------------------------------------------|:------------|
| id             | BIGINT      | 20   | NO     | -      | 主键                                          | PK          |
| quota_name     | VARCHAR     | 128  | NO     | -      | 配额名称                                      | -           |
| subject_type   | VARCHAR     | 16   | NO     | -      | 主体类型：DEPT/PROJECT                        | -           |
| subject_id     | BIGINT      | 20   | NO     | -      | 主体ID（部门ID 或项目ID）                     | IDX_SUBJECT |
| period         | VARCHAR     | 16   | NO     | -      | 配额周期：MONTH/WEEK（BR-COST-04）            | -           |
| token_limit    | BIGINT      | 20   | YES    | -      | Token 上限                                    | -           |
| amount_limit   | NUMERIC     | 12,2 | YES    | -      | 金额上限（与 Token 上限可二选一或同时配置）   | -           |
| warn_threshold | VARCHAR     | 64   | YES    | -      | 告警阈值（百分比，如 80,90；BR-COST-02）      | -           |
| reset_type     | VARCHAR     | 16   | NO     | AUTO   | 重置方式：AUTO-自动/MANUAL-手动（BR-COST-04） | -           |
| status         | SMALLINT    | 2    | NO     | 1      | 启用状态：1-启用,0-停用                       | -           |
| created_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                      | -           |
| updated_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                      | -           |

### 4.12 成本消耗表（cost_consumption）

| 字段名        | 类型        | 长度 | 允许空 | 默认值 | 说明                                   | 索引        |
|:--------------|:------------|:-----|:-------|:-------|:---------------------------------------|:------------|
| id            | BIGINT      | 20   | NO     | -      | 主键                                   | PK          |
| subject_type  | VARCHAR     | 16   | NO     | -      | 主体类型：DEPT/PROJECT                 | -           |
| subject_id    | BIGINT      | 20   | NO     | -      | 主体ID（部门ID 或项目ID）              | IDX_SUBJECT |
| model_id      | BIGINT      | 20   | NO     | -      | 关联模型配置ID（model_config）         | IDX_MODEL   |
| stat_date     | DATE        | -    | NO     | -      | 统计日期（支撑当日消耗，BR-COST-01）   | IDX_DATE    |
| stat_month    | VARCHAR     | 7    | NO     | -      | 统计月份，格式 YYYY-MM（支撑当月消耗） | -           |
| input_tokens  | BIGINT      | 20   | NO     | 0      | 输入 Token 累计                        | -           |
| output_tokens | BIGINT      | 20   | NO     | 0      | 输出 Token 累计                        | -           |
| total_tokens  | BIGINT      | 20   | NO     | 0      | 总 Token 累计                          | -           |
| amount        | NUMERIC     | 12,2 | NO     | 0      | 消耗金额（按模型单价计算，BR-COST-01） | -           |
| created_at    | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                               | -           |
| updated_at    | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                               | -           |

### 4.13 审计日志表（audit_log）

| 字段名         | 类型        | 长度 | 允许空 | 默认值 | 说明             | 索引     |
|:---------------|:------------|:-----|:-------|:-------|:-----------------|:---------|
| id             | BIGINT      | 20   | NO     | -      | 主键             | PK       |
| operator_id    | BIGINT      | 20   | NO     | -      | 操作人ID         | IDX_OP   |
| operator_name  | VARCHAR     | 64   | NO     | -      | 操作人姓名       | -        |
| operation_type | VARCHAR     | 32   | NO     | -      | 操作类型         | IDX_TYPE |
| target_type    | VARCHAR     | 32   | YES    | -      | 操作对象类型     | -        |
| target_id      | BIGINT      | 20   | YES    | -      | 操作对象ID       | -        |
| detail         | JSONB       | -    | YES    | -      | 操作详情（JSON） | -        |
| ip_address     | VARCHAR     | 64   | YES    | -      | IP 地址          | -        |
| created_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 操作时间         | IDX_DATE |
| updated_at     | TIMESTAMPTZ | -    | YES    | -      | 更新时间         | -        |

### 4.14 用户表（sys_user）

| 字段名     | 类型        | 长度 | 允许空 | 默认值 | 说明                      | 索引     |
|:-----------|:------------|:-----|:-------|:-------|:--------------------------|:---------|
| id         | BIGINT      | 20   | NO     | -      | 主键                      | PK       |
| username   | VARCHAR     | 64   | NO     | -      | 用户名                    | UK       |
| password   | VARCHAR     | 256  | NO     | -      | 密码（BCrypt 加密）       | -        |
| email      | VARCHAR     | 128  | YES    | -      | 邮箱                      | -        |
| phone      | VARCHAR     | 20   | YES    | -      | 手机号                    | -        |
| dept_id    | BIGINT      | 20   | YES    | -      | 所属部门ID                | IDX_DEPT |
| status     | SMALLINT    | 2    | NO     | 1      | 状态：1-启用,0-禁用       | -        |
| created_at | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                  | -        |
| updated_at | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                  | -        |
| deleted    | SMALLINT    | 2    | NO     | 0      | 逻辑删除：0-正常,1-已删除 | -        |

### 4.15 角色表（sys_role）

| 字段名      | 类型        | 长度 | 允许空 | 默认值 | 说明                                                                                | 索引 |
|:------------|:------------|:-----|:-------|:-------|:------------------------------------------------------------------------------------|:-----|
| id          | BIGINT      | 20   | NO     | -      | 主键                                                                                | PK   |
| role_code   | VARCHAR     | 32   | NO     | -      | 角色标识（v1.0 内置：ADMIN/PM/QA/DEVELOPER；AUDITOR/OPERATOR 为预留角色，后期扩展） | UK   |
| role_name   | VARCHAR     | 64   | NO     | -      | 角色名称                                                                            | -    |
| description | VARCHAR     | 256  | YES    | -      | 角色描述                                                                            | -    |
| is_system   | SMALLINT    | 2    | NO     | 0      | 是否系统内置：1-是,0-否                                                             | -    |
| created_at  | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                                                            | -    |
| updated_at  | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                                            | -    |

### 4.16 用户角色关联表（sys_user_role）

| 字段名  | 类型   | 长度 | 允许空 | 默认值 | 说明   | 索引     |
|:--------|:-------|:-----|:-------|:-------|:-------|:---------|
| id      | BIGINT | 20   | NO     | -      | 主键   | PK       |
| user_id | BIGINT | 20   | NO     | -      | 用户ID | IDX_USER |
| role_id | BIGINT | 20   | NO     | -      | 角色ID | IDX_ROLE |

### 4.17 部门表（sys_dept）

| 字段名     | 类型        | 长度 | 允许空 | 默认值 | 说明                 | 索引 |
|:-----------|:------------|:-----|:-------|:-------|:---------------------|:-----|
| id         | BIGINT      | 20   | NO     | -      | 主键                 | PK   |
| dept_name  | VARCHAR     | 64   | NO     | -      | 部门名称             | -    |
| parent_id  | BIGINT      | 20   | YES    | 0      | 父部门ID（0 表示根） | -    |
| sort_order | INTEGER     | 11   | YES    | 0      | 排序序号             | -    |
| leader     | VARCHAR     | 64   | YES    | -      | 负责人               | -    |
| status     | SMALLINT    | 2    | NO     | 1      | 状态：1-启用,0-禁用  | -    |
| created_at | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间             | -    |

### 4.18 项目表（project）

| 字段名       | 类型        | 长度 | 允许空 | 默认值 | 说明                        | 索引     |
|:-------------|:------------|:-----|:-------|:-------|:----------------------------|:---------|
| id           | BIGINT      | 20   | NO     | -      | 主键                        | PK       |
| project_code | VARCHAR     | 64   | NO     | -      | 项目标识                    | UK       |
| project_name | VARCHAR     | 128  | NO     | -      | 项目名称                    | -        |
| dept_id      | BIGINT      | 20   | YES    | -      | 归属部门ID（成本/数据归集） | IDX_DEPT |
| status       | SMALLINT    | 2    | NO     | 1      | 状态：1-启用,0-停用         | -        |
| created_at   | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                    | -        |
| updated_at   | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                    | -        |

### 4.19 仓库表（repo）

| 字段名             | 类型        | 长度 | 允许空 | 默认值 | 说明                                                                                                       | 索引        |
|:-------------------|:------------|:-----|:-------|:-------|:-----------------------------------------------------------------------------------------------------------|:------------|
| id                 | BIGINT      | 20   | NO     | -      | 主键                                                                                                       | PK          |
| repo_name          | VARCHAR     | 128  | NO     | -      | 仓库名称（与平台一致）                                                                                     | -           |
| platform_config_id | BIGINT      | 20   | NO     | -      | 关联平台接入ID（platform_config）                                                                          | IDX_PF      |
| external_repo_id   | VARCHAR     | 64   | YES    | -      | 平台侧仓库标识（GitLab project_id / GitHub full_name）                                                     | -           |
| project_id         | BIGINT      | 20   | YES    | -      | 归属项目ID（project）                                                                                      | IDX_PROJECT |
| default_branch     | VARCHAR     | 128  | YES    | -      | 默认分支                                                                                                   | -           |
| main_language      | VARCHAR     | 32   | YES    | -      | 主语言（FR-025 语言识别结果缓存）                                                                          | -           |
| test_strategy_id   | BIGINT      | 20   | YES    | -      | 绑定的测试策略ID（未绑定取全局默认，FR-010）                                                               | -           |
| is_secret          | SMALLINT    | 2    | NO     | 0      | 是否涉密仓库：1-是,0-否（`is_secret=1` 时禁止使用 `data_training=1` 的模型，见《Prompt 设计说明书》§11.2） | -           |
| status             | SMALLINT    | 2    | NO     | 1      | 状态：1-启用,0-停用                                                                                        | -           |
| created_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                                                                                   | -           |
| updated_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                                                                   | -           |

> 唯一约束：`(platform_config_id, external_repo_id)`，保证同一平台实例下仓库不重复登记。

### 4.20 变更意图分析表（review_change_analysis）

| 字段名                | 类型        | 长度 | 允许空 | 默认值 | 说明                                | 索引       |
|:----------------------|:------------|:-----|:-------|:-------|:------------------------------------|:-----------|
| id                    | BIGINT      | 20   | NO     | -      | 主键                                | PK         |
| task_id               | BIGINT      | 20   | NO     | -      | 关联任务ID                          | UK         |
| commit_id             | VARCHAR     | 64   | YES    | -      | 提交 SHA（缓存复用键，BR-05）       | IDX_COMMIT |
| change_type           | VARCHAR     | 32   | YES    | -      | FEATURE/BUGFIX/REFACTOR/CHORE/OTHER | -          |
| summary               | TEXT        | -    | YES    | -      | 变更摘要                            | -          |
| affected_files        | JSONB       | -    | YES    | -      | 影响文件列表                        | -          |
| implicit_requirements | JSONB       | -    | YES    | -      | 隐含需求点列表                      | -          |
| risk_points           | JSONB       | -    | YES    | -      | 风险点列表（含 level）              | -          |
| main_language         | VARCHAR     | 32   | YES    | -      | 主语言                              | -          |
| model_id              | BIGINT      | 20   | YES    | -      | 使用模型（model_config）            | -          |
| prompt_tokens         | INTEGER     | 11   | YES    | 0      | 输入 Token（BR-COST-01）            | -          |
| completion_tokens     | INTEGER     | 11   | YES    | 0      | 输出 Token（BR-COST-01）            | -          |
| from_cache            | SMALLINT    | 2    | NO     | 0      | 是否命中缓存：0-否,1-是（BR-05）    | -          |
| created_at            | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                            | -          |
| updated_at            | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                            | -          |

### 4.21 误报特征表（false_positive_feature）

| 字段名       | 类型        | 长度 | 允许空 | 默认值 | 说明                                               | 索引     |
|:-------------|:------------|:-----|:-------|:-------|:---------------------------------------------------|:---------|
| id           | BIGINT      | 20   | NO     | -      | 主键                                               | PK       |
| comment_id   | BIGINT      | 20   | YES    | -      | 关联评审意见ID                                     | IDX_CMT  |
| task_id      | BIGINT      | 20   | YES    | -      | 关联任务ID                                         | IDX_TASK |
| repo_id      | BIGINT      | 20   | YES    | -      | 关联仓库ID                                         | IDX_REPO |
| dimension    | VARCHAR     | 32   | YES    | -      | 维度                                               | IDX_DIM  |
| rule_code    | VARCHAR     | 32   | YES    | -      | 命中规则编号                                       | -        |
| language     | VARCHAR     | 32   | YES    | -      | 语言                                               | -        |
| file_path    | VARCHAR     | 512  | YES    | -      | 文件路径                                           | -        |
| code_snippet | TEXT        | -    | YES    | -      | 代码片段（问题行 ±10 行，已脱敏）                  | -        |
| ast_feature  | TEXT        | -    | YES    | -      | AST 节点类型序列（JSON 数组字符串）                | -        |
| embedding    | TEXT        | -    | YES    | -      | 语义向量（JSON 数组字符串；二期可迁移至 pgvector） | -        |
| created_at   | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                           | -        |

### 4.22 修复样本表（fix_sample）

| 字段名         | 类型        | 长度 | 允许空 | 默认值 | 说明                            | 索引     |
|:---------------|:------------|:-----|:-------|:-------|:--------------------------------|:---------|
| id             | BIGINT      | 20   | NO     | -      | 主键                            | PK       |
| comment_id     | BIGINT      | 20   | YES    | -      | 关联评审意见ID                  | IDX_CMT  |
| repo_id        | BIGINT      | 20   | YES    | -      | 关联仓库ID                      | IDX_REPO |
| dimension      | VARCHAR     | 32   | YES    | -      | 维度                            | IDX_DIM  |
| language       | VARCHAR     | 32   | YES    | -      | 语言                            | -        |
| problem_code   | TEXT        | -    | NO     | -      | 问题代码（已脱敏）              | -        |
| fixed_code     | TEXT        | -    | NO     | -      | 修复后代码（已脱敏）            | -        |
| reason         | VARCHAR     | 512  | YES    | -      | 一句话原因                      | -        |
| token_estimate | INTEGER     | 11   | YES    | 0      | 预估 Token（Few-shot 预算控制） | -        |
| use_count      | INTEGER     | 11   | NO     | 0      | 被抽中次数                      | -        |
| status         | SMALLINT    | 2    | NO     | 1      | 状态：1-可用,0-停用             | -        |
| created_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                        | -        |
| updated_at     | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                        | -        |

### 4.23 通知配置表（notification_config）

| 字段名             | 类型        | 长度 | 允许空 | 默认值 | 说明                                                                   | 索引        |
|:-------------------|:------------|:-----|:-------|:-------|:-----------------------------------------------------------------------|:------------|
| id                 | BIGINT      | 20   | NO     | -      | 主键                                                                   | PK          |
| config_name        | VARCHAR     | 128  | NO     | -      | 配置名称                                                               | -           |
| channel            | VARCHAR     | 32   | NO     | -      | 渠道：WECOM/DINGTALK/FEISHU/EMAIL                                      | IDX_CHANNEL |
| webhook_url        | VARCHAR     | 512  | YES    | -      | 渠道 Webhook 地址（含 token，AES-256 加密存储）                        | -           |
| secret             | VARCHAR     | 256  | YES    | -      | 加签密钥（AES-256 加密存储）                                           | -           |
| recipients         | VARCHAR     | 512  | YES    | -      | @指定人员（邮箱/手机号/企微 userid，逗号分隔）                         | -           |
| trigger_conditions | JSONB       | -    | YES    | -      | 触发条件数组：REVIEW_COMPLETED/BLOCKER_FOUND/TASK_FAILED/QUOTA_WARNING | -           |
| status             | SMALLINT    | 2    | NO     | 1      | 状态：1-启用,0-停用                                                    | -           |
| created_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                                                               | -           |
| updated_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                                                               | -           |

### 4.24 菜单表（sys_menu）

| 字段名          | 类型        | 长度 | 允许空 | 默认值 | 说明                       | 索引    |
|:----------------|:------------|:-----|:-------|:-------|:---------------------------|:--------|
| id              | BIGINT      | 20   | NO     | -      | 主键                       | PK      |
| parent_id       | BIGINT      | 20   | NO     | 0      | 父菜单ID（0 为根）         | IDX_PID |
| menu_name       | VARCHAR     | 64   | NO     | -      | 菜单名称                   | -       |
| menu_code       | VARCHAR     | 64   | NO     | -      | 菜单标识                   | UK      |
| menu_type       | SMALLINT    | 2    | NO     | 2      | 1-目录,2-菜单,3-按钮       | -       |
| path            | VARCHAR     | 256  | YES    | -      | 前端路由路径               | -       |
| component       | VARCHAR     | 256  | YES    | -      | 前端组件路径               | -       |
| icon            | VARCHAR     | 64   | YES    | -      | 图标                       | -       |
| permission_code | VARCHAR     | 64   | YES    | -      | 权限标识（如 review:list） | -       |
| sort_order      | INTEGER     | 11   | YES    | 0      | 排序序号                   | -       |
| visible         | SMALLINT    | 2    | NO     | 1      | 是否可见：1-是,0-否        | -       |
| created_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                   | -       |
| updated_at      | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                   | -       |

### 4.25 角色菜单关联表（sys_role_menu）

| 字段名  | 类型   | 长度 | 允许空 | 默认值 | 说明   | 索引     |
|:--------|:-------|:-----|:-------|:-------|:-------|:---------|
| id      | BIGINT | 20   | NO     | -      | 主键   | PK       |
| role_id | BIGINT | 20   | NO     | -      | 角色ID | IDX_ROLE |
| menu_id | BIGINT | 20   | NO     | -      | 菜单ID | IDX_MENU |

> 唯一约束：`(role_id, menu_id)`。

### 4.26 用户平台账号绑定表（sys_user_platform）

| 字段名             | 类型        | 长度 | 允许空 | 默认值 | 说明                          | 索引         |
|:-------------------|:------------|:-----|:-------|:-------|:------------------------------|:-------------|
| id                 | BIGINT      | 20   | NO     | -      | 主键                          | PK           |
| user_id            | BIGINT      | 20   | NO     | -      | 系统用户ID（sys_user）        | IDX_USER     |
| platform_config_id | BIGINT      | 20   | NO     | -      | 平台接入ID（platform_config） | IDX_PLATFORM |
| platform_user_id   | VARCHAR     | 64   | YES    | -      | 平台侧用户ID                  | -            |
| platform_username  | VARCHAR     | 128  | NO     | -      | 平台侧用户名                  | -            |
| platform_email     | VARCHAR     | 128  | YES    | -      | 平台侧邮箱                    | -            |
| created_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 创建时间                      | -            |
| updated_at         | TIMESTAMPTZ | -    | NO     | NOW()  | 更新时间                      | -            |

**作者解析策略（FR-022 补充规则）**：

1. 按 `platform_config_id + platform_username` 精确匹配 `sys_user_platform` → 命中则取 `user_id`；
2. 未命中时，按 Webhook 携带的 author email 匹配 `sys_user.email` → 命中则 **自动写入**一条绑定记录；
3. 仍未命中 → 使用内置兜底账号 `system_anonymous`（seed 初始化，`dept_id` 为空，`status=1`）作为 `author_id`，并写 WARN 审计日志；
4. `review_task.dept_id` 取解析到的用户 `dept_id` 冗余存储；兜底账号时为空（此类任务仅 ADMIN 可见）。

---

## 5 接口详细设计

> **契约文件**：全部 98 个接口的字段级请求/响应契约以机器可读的 `../api/openapi.yaml`（OpenAPI 3.1）为唯一事实依据，
> 本章仅作补充说明。前端可用 `openapi-generator` 直接生成 TS 类型与请求方法，后端据此生成 DTO；
> 两者不一致时以 `openapi.yaml` 为准。

### 5.1 API 总体规范

| 规范项   | 说明                                                               |
|:---------|:-------------------------------------------------------------------|
| 风格     | RPC（统一 POST）                                                   |
| URL 格式 | POST /api/v1/{module}/{action}                                     |
| 请求格式 | JSON Body，业务参数统一置于 param 字段                             |
| 响应格式 | {"code": 0, "message": "success", "data": {}, "requestId": "uuid"} |
| 认证方式 | Header Authorization: Bearer {token}                               |

**错误码规范**：

| 错误码 | 说明              |
|:-------|:------------------|
| 0      | 成功              |
| 40001  | 参数校验失败      |
| 40101  | 未登录/Token 过期 |
| 40301  | 无权限            |
| 40401  | 资源不存在        |
| 50001  | 系统内部错误      |
| 50002  | LLM 调用失败      |

### 5.2 评审概览模块接口

#### 5.2.1 概览总览

- **URL**：POST /api/v1/dashboard/summary
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "timeRange": {
        "start": "2026-08-09",
        "end": "2026-09-08"
      },
      "repoId": 1
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "totalReviews": 156,
      "totalIssues": 89,
      "averageScore": 82,
      "testCoverage": 76.5,
      "trend": {
        "reviewCount": "+12%",
        "issueCount": "-5%",
        "scoreTrend": "+3"
      }
    },
    "requestId": "abc-123"
  }
  ```

#### 5.2.2 问题分布

- **URL**：POST /api/v1/dashboard/issueDistribution
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "timeRange": {"start": "2026-08-09", "end": "2026-09-08"},
      "repoId": 1
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "data": {
      "byDimension": [
        {"dimension": "BUG", "count": 30},
        {"dimension": "SECURITY", "count": 20},
        {"dimension": "STYLE", "count": 15},
        {"dimension": "PERFORMANCE", "count": 12},
        {"dimension": "READABILITY", "count": 12}
      ],
      "bySeverity": [
        {"severity": "BLOCKER", "count": 5},
        {"severity": "CRITICAL", "count": 25},
        {"severity": "MINOR", "count": 59}
      ]
    }
  }
  ```

#### 5.2.3 质量趋势

- **URL**：POST /api/v1/dashboard/qualityTrend
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "granularity": "DAY",
      "timeRange": {"start": "2026-08-09", "end": "2026-09-08"}
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "data": {
      "scoreTrend": [
        {"date": "2026-09-01", "score": 80},
        {"date": "2026-09-02", "score": 82},
        {"date": "2026-09-03", "score": 78}
      ],
      "issueTrend": [
        {"date": "2026-09-01", "count": 10},
        {"date": "2026-09-02", "count": 8},
        {"date": "2026-09-03", "count": 12}
      ]
    }
  }
  ```

#### 5.2.4 仓库分析

- **URL**：POST /api/v1/dashboard/repoAnalysis
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "timeRange": {"start": "2026-08-09", "end": "2026-09-08"}
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "data": {
      "repos": [
        {
          "repoId": 1,
          "repoName": "backend-api",
          "reviewCount": 50,
          "issueCount": 25,
          "averageScore": 85,
          "activityScore": 90
        }
      ]
    }
  }
  ```

### 5.3 智能分析模块接口

#### 5.3.1 任务列表

- **URL**：POST /api/v1/review/list
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "page": 1,
      "size": 20,
      "repoName": "backend-api",
      "authorId": 1,
      "status": "COMPLETED",
      "startTime": "2026-09-01",
      "endTime": "2026-09-08",
      "keyword": "login"
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "data": {
      "total": 156,
      "page": 1,
      "size": 20,
      "list": [
        {
          "taskId": 10023,
          "taskNo": "TR-20260908-001",
          "repoName": "backend-api",
          "mrTitle": "Fix login bug",
          "author": "张三",
          "triggerTime": "2026-09-08 10:00:00",
          "status": "COMPLETED",
          "score": 85,
          "hasTestCases": true
        }
      ]
    }
  }
  ```

#### 5.3.2 获取评审报告

- **URL**：POST /api/v1/review/getReport
- **Method**：POST
- **职责分工**：**详情页首屏聚合接口**，一次返回任务信息 + 三个 Tab 的全量数据（`changeAnalysis` / `comments` / `testCases`）。与之配套的 `getChangeAnalysis`、`getReviewComments`、`getTestCases` 用于超大数据量的分片/懒加载场景（见 §5.3.6~§5.3.8），本章三者并存、**不重复计入接口总数**。
- **Request**：
  ```json
  {
    "param": {
      "taskId": 10023
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "data": {
      "taskInfo": {
        "taskNo": "TR-20260908-001",
        "status": "COMPLETED",
        "score": 85
      },
      "changeAnalysis": {
        "changeType": "FEATURE",
        "mainLanguage": "Java",
        "fromCache": false,
        "summary": "本次变更主要修改了登录逻辑...",
        "affectedFiles": ["Login.java", "AuthService.java"],
        "implicitRequirements": ["用户认证流程"],
        "riskPoints": [
          {"desc": "SQL 拼接存在注入风险", "level": "HIGH"},
          {"desc": "密码加密变更未兼容历史数据", "level": "MEDIUM"}
        ]
      },
      "comments": [
        {
          "id": 9001,
          "filePath": "Login.java",
          "lineNo": 45,
          "dimension": "SECURITY",
          "severity": "BLOCKER",
          "title": "SQL 语句字符串拼接存在注入风险",
          "description": "第 45 行使用字符串拼接构造 SQL，入参 username 未经参数化即进入查询。",
          "suggestion": "使用参数化查询",
          "confidence": 0.92,
          "ruleCode": "SEC-001",
          "confirmStatus": 0
        }
      ],
      "testCases": [
        {
          "id": 7001,
          "caseName": "TestLoginSuccess",
          "scenario": "POSITIVE",
          "preCondition": "用户已注册",
          "steps": "输入正确账号密码",
          "expectedResult": "登录成功",
          "filePath": "Login.java",
          "lineNo": 45,
          "source": 0,
          "verifyStatus": 0
        }
      ]
    }
  }
  ```

#### 5.3.3 确认评审意见

- **URL**：POST /api/v1/review/confirmComment
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "commentId": 1,
      "confirmStatus": 1
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "message": "success"
  }
  ```

#### 5.3.4 重试评审任务

- **URL**：POST /api/v1/review/retry
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "taskId": 10023
    }
  }
  ```

#### 5.3.5 变更追溯

- **URL**：POST /api/v1/review/getTrace
- **Method**：POST
- **Request**：
  ```json
  {
    "param": {
      "taskId": 10023
    }
  }
  ```
- **Response**：
  ```json
  {
    "code": 0,
    "data": {
      "nodes": [
        {"id": "file1", "type": "FILE", "name": "Login.java"},
        {"id": "req1", "type": "REQUIREMENT", "name": "用户认证"},
        {"id": "issue1", "type": "ISSUE", "name": "SQL注入风险"},
        {"id": "test1", "type": "TESTCASE", "name": "TestLoginSuccess"}
      ],
      "edges": [
        {"from": "file1", "to": "issue1", "relation": "contains"},
        {"from": "req1", "to": "file1", "relation": "implements"},
        {"from": "issue1", "to": "test1", "relation": "coveredBy"}
      ]
    }
  }
  ```

#### 5.3.6 变更解读

- **URL**：POST /api/v1/review/getChangeAnalysis
- **Method**：POST
- **职责分工（与 getReport 的关系）**：与 `getReport` 的 `data.changeAnalysis` 段同源（读 `review_change_analysis`，LLD §4.20）。**详情页首屏走 `getReport` 一次聚合**；本接口用于 Tab1 单独刷新、或变更意图结果过大时的按需加载。
- **Request**：`{"param":{"taskId":10023}}`
- **Response 要点**：结构与 `getReport.data.changeAnalysis` 完全一致（`changeType`、`mainLanguage`、`fromCache`、`summary`、`affectedFiles`、`implicitRequirements`、`riskPoints[{desc,level}]`）。

#### 5.3.7 评审意见

- **URL**：POST /api/v1/review/getReviewComments
- **Method**：POST
- **职责分工（与 getReport 的关系）**：返回单个任务的评审意见列表。**评论条数超过 200 条**时前端不依赖 `getReport` 一次返回，改由本接口按 `taskId` 单独拉取（未来可加分页参数）。
- **Request**：`{"param":{"taskId":10023}}`
- **Response 要点**：`comments[]` 元素结构与 `getReport.data.comments` 一致（`id`、`filePath`、`lineNo`、`dimension`、`severity`、`title`、`description`、`suggestion`、`confidence`、`ruleCode`、`confirmStatus`），并与《Prompt（指令）设计说明书》§4.2.3 输出 Schema、`review_comment` 表（§4.2）字段对齐。

#### 5.3.8 测试用例

- **URL**：POST /api/v1/review/getTestCases
- **Method**：POST
- **职责分工（与 getReport 的关系）**：返回单个任务的测试用例列表。**用例超过 200 条**或需单独刷新 Tab3 时使用，避免与 Diff/评论数据一起挤在首屏响应中。
- **Request**：`{"param":{"taskId":10023}}`
- **Response 要点**：`testCases[]` 元素结构与 `getReport.data.testCases` 一致（`id`、`caseName`、`scenario`、`preCondition`、`steps`、`expectedResult`、`filePath`、`lineNo`、`source`、`verifyStatus`），对齐 `test_case` 表（§4.3）。

#### 5.3.9 驳回评审意见

- **URL**：POST /api/v1/review/rejectComment
- **Method**：POST

#### 5.3.10 补充测试用例

- **URL**：POST /api/v1/review/addTestCase
- **Method**：POST

#### 5.3.11 标注测试状态

- **URL**：POST /api/v1/review/updateTestCaseStatus
- **Method**：POST

#### 5.3.12 手动触发评审

- **URL**：POST /api/v1/review/trigger
- **Method**：POST

### 5.4 规则配置模块接口

#### 5.4.1 指令管理

- **列表**：POST /api/v1/prompt/list
- **详情**：POST /api/v1/prompt/getById
- **创建**：POST /api/v1/prompt/create
- **更新**：POST /api/v1/prompt/update
- **删除**：POST /api/v1/prompt/delete
- **版本历史**：POST /api/v1/prompt/getVersions
- **版本回滚**：POST /api/v1/prompt/rollback
- **效果评估**：POST /api/v1/prompt/evaluate

#### 5.4.2 评审规则

- **列表**：POST /api/v1/rule/list
- **详情**：POST /api/v1/rule/getById
- **创建**：POST /api/v1/rule/create
- **更新**：POST /api/v1/rule/update
- **删除**：POST /api/v1/rule/delete
- **切换状态**：POST /api/v1/rule/toggleStatus

#### 5.4.3 测试策略

- **列表**：POST /api/v1/testStrategy/list
- **创建**：POST /api/v1/testStrategy/create
- **更新**：POST /api/v1/testStrategy/update
- **删除**：POST /api/v1/testStrategy/delete
- **策略建议**：POST /api/v1/testStrategy/suggest

#### 5.4.4 规则模板

- **列表**：POST /api/v1/template/list
- **启用**：POST /api/v1/template/apply
- **创建**：POST /api/v1/template/create
- **更新**：POST /api/v1/template/update
- **删除**：POST /api/v1/template/delete

### 5.5 运维管理模块接口

- **系统监控指标**：POST /api/v1/monitor/metrics
- **系统健康检查**：POST /api/v1/monitor/health
- **审计日志列表**：POST /api/v1/audit/list
- **审计日志导出**：POST /api/v1/audit/export

### 5.6 权限管理模块接口

- **用户列表**：POST /api/v1/user/list
- **用户详情**：POST /api/v1/user/getById
- **创建用户**：POST /api/v1/user/create
- **更新用户**：POST /api/v1/user/update
- **删除用户**：POST /api/v1/user/delete
- **重置密码**：POST /api/v1/user/resetPassword
- **分配用户角色**：POST /api/v1/user/assignRoles
- **LDAP/OIDC 同步**：POST /api/v1/user/sync
- **角色列表**：POST /api/v1/role/list
- **角色详情**：POST /api/v1/role/getById
- **创建角色**：POST /api/v1/role/create
- **更新角色**：POST /api/v1/role/update
- **删除角色**：POST /api/v1/role/delete
- **查询角色权限**：POST /api/v1/role/getPermissions
- **分配角色权限**：POST /api/v1/role/assignPermissions
- **部门树**：POST /api/v1/dept/tree
- **部门详情**：POST /api/v1/dept/getById
- **创建部门**：POST /api/v1/dept/create
- **更新部门**：POST /api/v1/dept/update
- **删除部门**：POST /api/v1/dept/delete
- **数据权限列表**：POST /api/v1/dataPermission/list
- **创建数据权限**：POST /api/v1/dataPermission/create
- **更新数据权限**：POST /api/v1/dataPermission/update
- **删除数据权限**：POST /api/v1/dataPermission/delete

### 5.7 系统配置模块接口

- **平台接入列表**：POST /api/v1/platform/list
- **创建平台接入**：POST /api/v1/platform/create
- **更新平台接入**：POST /api/v1/platform/update
- **删除平台接入**：POST /api/v1/platform/delete
- **平台连通性测试**：POST /api/v1/platform/testConnection
- **模型配置列表**：POST /api/v1/model/list
- **创建模型配置**：POST /api/v1/model/create
- **更新模型配置**：POST /api/v1/model/update
- **删除模型配置**：POST /api/v1/model/delete
- **模型连通性测试**：POST /api/v1/model/testConnection
- **通知设置列表**：POST /api/v1/notification/list
- **创建通知设置**：POST /api/v1/notification/create
- **更新通知设置**：POST /api/v1/notification/update
- **删除通知设置**：POST /api/v1/notification/delete
- **通知发送测试**：POST /api/v1/notification/testSend

### 5.8 成本管理模块接口（FR-021）

- **配额规则列表**：POST /api/v1/cost/quotaList
- **配额规则详情**：POST /api/v1/cost/getQuota
- **创建配额规则**：POST /api/v1/cost/createQuota
- **更新配额规则**：POST /api/v1/cost/updateQuota
- **删除配额规则**：POST /api/v1/cost/deleteQuota
- **消耗统计**：POST /api/v1/cost/consumption
- **配额状态**：POST /api/v1/cost/quotaStatus

### 5.9 认证模块接口

- **用户登录**：POST /api/v1/auth/login
- **刷新 Token**：POST /api/v1/auth/refreshToken
- **用户登出**：POST /api/v1/auth/logout
- **获取当前用户信息**：POST /api/v1/auth/getCurrentUser

### 5.10 核心引擎接口

- **Webhook 接收**：POST /api/v1/webhook/receive
- **人工重试**：POST /api/v1/webhook/retry
- **反馈上报**：POST /api/v1/feedback/report
- **效果报表**：POST /api/v1/feedback/feedbackReport

---

## 6 状态机设计

### 6.1 主状态（review_task.status）

| 状态码 | 状态名称 | 说明                     |
|:-------|:---------|:-------------------------|
| 0      | 待处理   | 任务已接收，尚未开始处理 |
| 1      | 处理中   | 任务正在处理中           |
| 2      | 已完成   | 任务已成功完成           |
| 3      | 失败     | 任务处理失败             |
| 4      | 已取消   | 任务已被取消             |

**流转约束**：主状态遵循单向不可逆原则（0 → 1 → 2/3/4），禁止主状态在 2/3/4 之间互转，禁止回退至 0-待处理。

### 6.2 业务状态（review_task.sub_status）及流转规则

| 当前状态        | 触发事件               | 下一状态        | 处理逻辑                |
|:----------------|:-----------------------|:----------------|:------------------------|
| RECEIVED        | 配额不足               | SKIPPED_QUOTA   | 跳过评审，MR 留言提示   |
| RECEIVED        | 开始解析               | PARSING         | 接收 Webhook 后开始解析 |
| PARSING         | 解析成功               | QUEUED          | 解析完成，等待调度      |
| PARSING         | 解析失败               | FAILED          | 记录错误码              |
| QUEUED          | 调度成功               | ANALYZING       | 开始 LLM 分析           |
| QUEUED          | 用户取消               | CANCELLED       | 排队中可取消            |
| ANALYZING       | 分析成功且需生成测试   | GENERATING_TEST | 进入测试生成            |
| ANALYZING       | 分析成功且关闭测试生成 | WRITING_BACK    | 直接回写                |
| ANALYZING       | LLM 超时               | RETRYING        | 按策略重试              |
| ANALYZING       | 重试超限               | FAILED          | 终止任务                |
| ANALYZING       | LLM 不可用             | DEGRADED        | 降级完成                |
| ANALYZING       | 用户取消               | CANCELLED       | 分析中可取消            |
| RETRYING        | 重试成功               | ANALYZING       | 回到分析中              |
| RETRYING        | 重试失败               | FAILED          | 终止任务                |
| GENERATING_TEST | 生成成功               | WRITING_BACK    | 进入回写                |
| GENERATING_TEST | 生成失败               | PARTIAL_SUCCESS | 评审成功但测试生成失败  |
| GENERATING_TEST | 用户取消               | CANCELLED       | 测试生成中可取消        |
| WRITING_BACK    | 回写成功               | COMPLETED       | 任务完成                |
| WRITING_BACK    | 回写失败               | PARTIAL_SUCCESS | 分析成功但回写失败      |
| WRITING_BACK    | 超时                   | TIMEOUT         | 回写超时                |
| TIMEOUT         | 自动重试               | RETRYING        | 可配置                  |
| TIMEOUT         | 重试超限               | FAILED          | 终止任务                |
| PARTIAL_SUCCESS | 人工重试回写           | WRITING_BACK    | 重新回写                |
| DEGRADED        | 人工重试               | ANALYZING       | 重新分析                |

### 6.2.1 Prompt 渲染与 LLM 调用的阶段归属

为避免状态订阅语义混淆，明确如下：

| 环节                                                 | 所属业务状态                    | 失败流转                         |
|:-----------------------------------------------------|:--------------------------------|:---------------------------------|
| Diff 获取、AST 解析、超限文件过滤（FR-025 前置）     | `PARSING`                       | `PARSING → FAILED`               |
| 指令渲染（`PromptRenderEngine`）、阶段一变更意图分析 | `ANALYZING`                     | `ANALYZING → FAILED`             |
| 阶段二评审意见生成（维度分桶）、阶段三测试用例生成   | `ANALYZING` / `GENERATING_TEST` | 见 §3.7.2（单维度/单阶段先重试） |

> `PromptRenderException`、`PromptVariableException`、`PromptCircularRefException` **不得**复用 `PARSING → FAILED`；按《Prompt（指令）设计说明书》§3.3 处理后统一归入 `ANALYZING → FAILED`。

### 6.3 主状态 ↔ 业务状态映射

| 主状态   | 业务状态                                                    |
|:---------|:------------------------------------------------------------|
| 0 待处理 | RECEIVED、PARSING、QUEUED                                   |
| 1 处理中 | ANALYZING、GENERATING_TEST、WRITING_BACK、RETRYING、TIMEOUT |
| 2 已完成 | COMPLETED、PARTIAL_SUCCESS、DEGRADED                        |
| 3 失败   | FAILED                                                      |
| 4 已取消 | CANCELLED、SKIPPED_QUOTA                                    |

### 6.4 受控回退规则

仅允许以下三条受控回退，须记录审计日志并累加重试次数：

1. TIMEOUT → RETRYING
2. PARTIAL_SUCCESS → WRITING_BACK
3. DEGRADED → ANALYZING

---

## 7 安全与权限设计

### 7.1 认证

- **协议**：JWT (HS256)
- **Header**：Authorization: Bearer <token>
- **拦截器**：AuthInterceptor 校验 Token 有效性，解析 userId 和 role 放入 ThreadLocal
- **Token 有效期**：默认 7 天，支持刷新机制

### 7.2 数据权限

- **实现方式**：MyBatis Interceptor 或 AOP，**由 `data_permission` 表驱动**（FR-017 的可配置模型），内置角色策略仅作为未配置时的兜底。
- **判定优先级**：
    1. 命中 `data_permission` 记录时，按其 `scope` 过滤：
        - `ALL` → 不加过滤条件；
        - `DEPT_AND_SUB` → `dept_id IN (:userDeptTree)`（**当前部门及全部下级部门**，与 §3.2.1 一致）；
        - `SELF` → `author_id = :currentUserId`；
    2. **未配置任何记录时**，按内置角色默认策略兜底：ADMIN = `ALL`；PM / QA = `DEPT_AND_SUB`；DEVELOPER = `SELF`。
- **适用资源**：`resource_type` = `REVIEW_TASK`（`review_task`）/ `DASHBOARD`（概览与统计）/ `RULE`（规则与指令）。多资源同时命中时按最严格的 scope 取交集。
- **预留角色**：`AUDITOR`/`OPERATOR` 为后期扩展预留角色，本期（v1.0）不实现，启用时权限按 SRS §7.3 落盘。
- **配置表**：`data_permission`（主体 `ROLE`/`DEPT` × 资源 × 范围）
- **前端约束**：数据权限过滤全部在后端 SQL 层完成，前端不传递 `deptId`/`authorId` 等数据范围参数（前端设计说明书 §5.1）。

### 7.3 敏感数据脱敏

- **触发时机**：在 LLMClient 发送请求前，调用 SensitiveDataMasker
- **脱敏规则**：
    - API Key：[a-zA-Z0-9]{32,} → ***REDACTED***
    - Password："password"\s*:\s*"[^"]*" → "password": "***REDACTED***"
    - 手机号：1[3-9]\d{9} → 1*** ****\d{4}
    - 邮箱：用户名部分保留前 3 位
    - Secret：`"secret"\s*:\s*"[^"]*"`（含 webhook_secret、api_secret 等） → `"secret": "***REDACTED***"`

### 7.4 密钥管理

- **存储加密**：所有敏感配置（Access Token、Webhook Secret、API Key）使用 AES-256 加密存储
- **传输加密**：所有外部 API 调用使用 HTTPS
- **密钥轮换**：支持定期轮换密钥，旧密钥保留过渡期

---

## 8 异常处理与日志

### 8.1 全局异常处理

- **BusinessException** → 返回 code: 40001, message: e.getMessage ()
- **LLMTimeoutException** → 触发重试机制或标记 RETRYING 状态
- **LLMUnavailableException** → 触发降级策略，标记 DEGRADED
- **PlatformConnectionException** → 标记 FAILED，记录错误信息
- **UnhandledException** → 返回 code: 50001, message: "系统内部错误"

### 8.2 日志规范

- **格式**：JSON
- **字段**：traceId, timestamp, level, thread, class, message, userId
- **关键日志点**：
    1. Webhook 接收原始 Payload（脱敏后）
    2. LLM 请求 Prompt 和 Response（脱敏后，用于调试）
    3. 状态机变更日志（当前状态 → 下一状态，触发事件）
    4. 重试日志（重试次数、延迟时间）
    5. 降级日志（降级原因、降级模式）
    6. 成本消耗日志（Token 用量、费用）

### 8.3 链路追踪

- **标识分工**（《编码规范》§7.1）：
    - `requestId`：客户端生成（UUID），置于**请求/响应体顶层**，用于单次请求的排查与前端日志；
    - `traceId`：服务端在 **Webhook 入口**生成，贯穿 Webhook → Kafka → Worker → LLM 出站，用于全链路聚合。
- **传播方式**：`traceId` 通过 HTTP 头 **`X-Trace-Id`** 透传；内部 RPC 与 Kafka 消息（`payload` 外的 `traceId` 字段，见 §10.2）继续携带。
- **MDC 约定**：请求入口将 `traceId`、`requestId`、`userId` 写入 MDC，日志框架统一输出；缺省时由服务端生成。
- **收集方式**：日志中统一输出 traceId，便于日志系统聚合追踪。

---

## 9 非功能需求设计

### 9.1 性能

| 指标                         | 目标值                  |
|:-----------------------------|:------------------------|
| Webhook 接收响应时间         | ≤ 200ms                 |
| 并发 Webhook 处理能力        | ≥ 50 QPS                |
| 智能分析引擎单次任务处理时间 | ≤ 300s（P95，标准模式） |
| API 接口 P99 响应时间        | ≤ 2 秒                  |
| 数据库查询 P99 响应时间      | ≤ 500ms                 |

### 9.2 可用性

- 系统可用性 ≥ 99.9%
- 支持多实例部署 + 负载均衡
- 无状态服务设计，支持水平扩展

### 9.3 可扩展性

- Webhook 解析层采用策略模式，新增平台只需实现适配接口
- LLM 调用层采用适配器模式，新增提供商只需实现适配接口
- 评审维度支持自定义维度，无需修改核心代码
- 测试用例生成模块可独立关闭

### 9.4 安全性

- JWT 认证 + RBAC 权限控制
- 敏感数据脱敏（API Key、密码、Secret、手机号、邮箱）
- AES-256 加密存储所有密钥
- HTTPS 传输加密
- SQL 注入防护（参数化查询）
- Webhook 签名校验

### 9.5 可靠性

- LLM 调用超时重试（默认 60s，最多 3 次，间隔 5s/30s/120s）
- MQ 重试不依赖 HTTP 重试
- 幂等性设计（Webhook 事件去重，Redis SETNX）
- 降级策略（主模型不可用时切换备用模型）
- 配额熔断（达 100% 配额暂停评审）

### 9.6 可观测性

- 结构化日志（JSON 格式）
- 链路追踪（traceId）
- 系统监控（QPS、延迟、成功率、Token 消耗、队列积压）
- 审计日志（操作人、操作类型、操作对象、操作详情、IP、时间，默认保留 180 天，可由 `audit.retention.days` 配置）

---

## 10 异步消息契约（Kafka）

### 10.1 Topic 清单

| Topic                  | 生产者                     | 消费者        | 分区键                        | 分区数 | 说明                                        |
|:-----------------------|:---------------------------|:--------------|:------------------------------|:-------|:--------------------------------------------|
| `TOPIC_REVIEW_REQUEST` | `aicr-webhook`、`aicr-web` | `aicr-worker` | `{platformConfigId}_{repoId}` | 12     | 评审请求（FR-022 / FR-005 手动触发）        |
| `TOPIC_REVIEW_RETRY`   | `aicr-web`                 | `aicr-worker` | 同上                          | 6      | 人工重试（`review/retry`、`webhook/retry`） |
| `TOPIC_WRITEBACK`      | `aicr-engine`(worker)      | `aicr-worker` | 同上                          | 6      | 结果回写（FR-024），独立重试不影响分析结果  |
| `TOPIC_FEEDBACK`       | `aicr-web`                 | `aicr-worker` | `{taskId}`                    | 3      | 反馈闭环（FR-026）                          |
| `TOPIC_NOTIFY`         | `aicr-worker`              | `aicr-worker` | `{channel}`                   | 3      | 通知发送（FR-020），失败不阻塞主流程        |
| `TOPIC_REVIEW_DLQ`     | `aicr-worker`              | 人工/运维     | -                             | 3      | 死信队列（重试耗尽）                        |

**分区键设计理由**：以 `{platformConfigId}_{repoId}` 分区，保证同一仓库的消息进入同一分区，配合单分区顺序消费与 Redis 分布式锁
`aicr:lock:repo:{repoId}`，实现 BR-07「同仓库串行、跨仓库并行」。

### 10.2 消息通用结构

所有 Topic 消息体统一封装：

```json
{
  "msgId": "3f8a1c2e-9b7d-4a51-8e0f-1c2d3e4f5a6b",
  "traceId": "7c1e9a3b-5d2f-4e8a-9b0c-1d2e3f4a5b6c",
  "eventType": "REVIEW_REQUEST",
  "occurredAt": "2026-09-08T10:00:00+08:00",
  "payload": {}
}
```

| 字段         | 说明                                                |
|:-------------|:----------------------------------------------------|
| `msgId`      | 消息唯一 ID，消费幂等键                             |
| `traceId`    | 链路追踪 ID（LLD §8.3），Webhook 入口生成并全程透传 |
| `eventType`  | 事件类型，见 §10.3                                  |
| `occurredAt` | 事件发生时间（ISO-8601，带时区）                    |
| `payload`    | 业务负载，各事件不同，见 §10.3                      |

### 10.3 事件类型与 Payload

| eventType        | Topic                  | Payload 关键字段                                                                                                     |
|:-----------------|:-----------------------|:---------------------------------------------------------------------------------------------------------------------|
| `REVIEW_REQUEST` | `TOPIC_REVIEW_REQUEST` | `taskId`、`taskNo`、`platformConfigId`、`repoId`、`repoName`、`mrId`、`commitId`、`action`、`eventId`、`triggeredBy` |
| `REVIEW_RETRY`   | `TOPIC_REVIEW_RETRY`   | `taskId`、`retryFrom`（DEGRADED / PARTIAL_SUCCESS / FAILED / TIMEOUT）、`operatorId`                                 |
| `WRITEBACK`      | `TOPIC_WRITEBACK`      | `taskId`、`commentIds[]`、`testCaseIds[]`                                                                            |
| `FEEDBACK`       | `TOPIC_FEEDBACK`       | `taskId`、`commentId`、`feedbackType`（CONFIRM / REJECT）、`operatorId`                                              |
| `NOTIFY`         | `TOPIC_NOTIFY`         | `channel`、`templateCode`、`params`、`recipients`                                                                    |

**`REVIEW_REQUEST` payload 示例**：

```json
{
  "taskId": 10023,
  "taskNo": "TR-20260908-001",
  "platformConfigId": 1,
  "repoId": 12,
  "repoName": "backend-api",
  "mrId": "88",
  "commitId": "a1b2c3d4e5f6",
  "action": "open",
  "eventId": "x-gitlab-event-uuid",
  "triggeredBy": 0
}
```

### 10.4 消费幂等

| 层级   | 机制                                                                       |
|:-------|:---------------------------------------------------------------------------|
| 消息层 | `aicr:mq:consumed:{msgId}` Redis SETNX，TTL 7 天；命中则直接 ACK           |
| 业务层 | `review_task` 唯一约束 `(repo_id, mr_id, commit_id)`；已存在且非终态则跳过 |
| 状态层 | 消费前校验 `sub_status`，终态（COMPLETED/FAILED/CANCELLED）消息直接丢弃    |

### 10.5 重试与死信

- 采用 Spring Kafka `@RetryableTopic`：`attempts = 4`（1 次正常 + 3 次重试），退避 **5s / 30s / 120s**（SRS 4.3 可靠性要求）；
- 重试耗尽投递 `TOPIC_REVIEW_DLQ`，同时任务置 `FAILED` 并写 `error_msg`，触发 `TASK_FAILED` 通知（FR-020）；
- **LLM 调用超时重试**（BR-09，默认 60s，最多 3 次，间隔 5s/30s/120s）在 `engine` 内部完成， **不依赖 MQ 重投递**，避免整任务重跑；
- 通知类消息（FR-020）发送失败仅记录日志，不进 DLQ、不阻塞主流程。

### 10.6 与状态机的联动

| 消费动作                   | 状态流转                         |
|:---------------------------|:---------------------------------|
| 消费 `REVIEW_REQUEST` 成功 | `RECEIVED → PARSING`             |
| Diff 获取与解析成功        | `PARSING → QUEUED`               |
| 调度进入分析               | `QUEUED → ANALYZING`             |
| 分析完成且需生成测试       | `ANALYZING → GENERATING_TEST`    |
| 分析完成且关闭测试         | `ANALYZING → WRITING_BACK`       |
| 回写成功                   | `WRITING_BACK → COMPLETED`       |
| 回写失败                   | `WRITING_BACK → PARTIAL_SUCCESS` |

---

## 附录

### 附录 A：模块与菜单对应关系

| 主菜单   | 子菜单               | 对应需求    |
|:---------|:---------------------|:------------|
| 评审概览 | 概览总览             | FR-001      |
| 评审概览 | 问题分布             | FR-002      |
| 评审概览 | 质量趋势             | FR-003      |
| 评审概览 | 仓库分析             | FR-004      |
| 智能分析 | 任务列表             | FR-005      |
| 智能分析 | 分析报告（变更解读） | FR-006 Tab1 |
| 智能分析 | 分析报告（评审意见） | FR-006 Tab2 |
| 智能分析 | 分析报告（测试用例） | FR-006 Tab3 |
| 智能分析 | 变更追溯             | FR-007      |
| 规则配置 | 指令管理             | FR-008      |
| 规则配置 | 评审规则             | FR-009      |
| 规则配置 | 测试策略             | FR-010      |
| 规则配置 | 规则模板             | FR-011      |
| 运维管理 | 系统监控             | FR-012      |
| 运维管理 | 审计日志             | FR-013      |
| 权限管理 | 用户管理             | FR-014      |
| 权限管理 | 角色管理             | FR-015      |
| 权限管理 | 部门管理             | FR-016      |
| 权限管理 | 数据权限             | FR-017      |
| 系统配置 | 平台接入             | FR-018      |
| 系统配置 | 模型配置             | FR-019      |
| 系统配置 | 通知设置             | FR-020      |
| 系统配置 | 成本管理             | FR-021      |
| 核心引擎 | Webhook 接收与解析   | FR-022      |
| 核心引擎 | 智能分析引擎         | FR-023      |
| 核心引擎 | 结果回写             | FR-024      |
| 核心引擎 | 大变更处理策略       | FR-025      |
| 核心引擎 | 反馈闭环与模型优化   | FR-026      |

---

### 附录 B：完整状态清单

| 状态码          | 状态名称           | 主状态映射 |
|:----------------|:-------------------|:-----------|
| RECEIVED        | 已接收             | 0 待处理   |
| PARSING         | 解析中             | 0 待处理   |
| QUEUED          | 排队中             | 0 待处理   |
| ANALYZING       | 分析中             | 1 处理中   |
| GENERATING_TEST | 测试生成中         | 1 处理中   |
| WRITING_BACK    | 回写中             | 1 处理中   |
| COMPLETED       | 已完成             | 2 已完成   |
| PARTIAL_SUCCESS | 部分成功           | 2 已完成   |
| FAILED          | 失败               | 3 失败     |
| TIMEOUT         | 超时               | 1 处理中   |
| CANCELLED       | 已取消             | 4 已取消   |
| RETRYING        | 重试中             | 1 处理中   |
| DEGRADED        | 降级完成           | 2 已完成   |
| SKIPPED_QUOTA   | 已跳过（配额不足） | 4 已取消   |

---

### 附录 C：架构约束固化配置示例

为在构建期强制 HLD §2.4.4 / §2.4.5 的依赖红线，采用 Maven Enforcer（按模块局部禁用）+ ArchUnit（分层测试）双保险。

#### C.1 Maven Enforcer（各模块 pom 内局部配置）

Maven Enforcer 的 `bannedDependencies` 为全局禁用，无法按"来源模块"区分，故约束写在被限制模块自身的 `pom.xml` 中：

`aicr-engine/pom.xml` —— 禁止依赖 `service`：

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>ban-service</id>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <bannedDependencies>
                        <excludes>
                            <exclude>com.joyintech:aicr-service</exclude>
                        </excludes>
                        <message>engine 禁止依赖 service（HLD §2.4.3 红线一）</message>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

`aicr-webhook/pom.xml` —— 禁止依赖 `service` 与 `engine`（`<excludes>` 增加 `com.joyintech:aicr-service`、
`com.joyintech:aicr-engine`）。

`aicr-web/pom.xml` —— 禁止依赖 `engine`（`<excludes>` 增加 `com.joyintech:aicr-engine`）。

`aicr-common/pom.xml` —— 禁止引入 Spring（`<excludes>` 增加 `org.springframework:*`）。

#### C.2 ArchUnit 分层测试（进 CI）

`aicr-worker/src/test/java/com/joyintech/aicr/ArchitectureTest.java`：

```java

@AnalyzeClasses(packages = "com.joyintech.aicr")
class ArchitectureTest {
    @ArchTest
    static final ArchRule moduleLayers = layeredArchitecture()
            .layer("common").definedBy("..common..")
            .layer("api").definedBy("..api..")
            .layer("base").definedBy("..base..")
            .layer("security").definedBy("..security..")
            .layer("service").definedBy("..service..")
            .layer("engine").definedBy("..engine..")
            .layer("web").definedBy("..web..")
            .layer("webhook").definedBy("..webhook..")
            .layer("worker").definedBy("..worker..")
            .whereLayer("common").mayOnlyBeAccessedByLayers("api", "base", "security", "service", "engine", "web", "webhook", "worker")
            .whereLayer("api").mayOnlyBeAccessedByLayers("base", "security", "service", "engine", "web", "webhook", "worker")
            .whereLayer("base").mayOnlyBeAccessedByLayers("security", "service", "engine", "web", "webhook", "worker")
            .whereLayer("security").mayOnlyBeAccessedByLayers("service", "engine", "web", "webhook", "worker")
            .whereLayer("service").mayOnlyBeAccessedByLayers("web", "worker")
            .whereLayer("engine").mayOnlyBeAccessedByLayers("worker")
            .whereLayer("worker").mayOnlyBeAccessedByLayers();
}
```

> 注：`engine` 的 `mayOnlyBeAccessedByLayers("worker")` 仅允许 `worker` 访问 `engine`，隐含禁止 `service`/`web` 依赖
> `engine`；结合 C.1（Maven Enforcer 禁止 `web→engine`）与 HLD §2.4.3 红线二（web 禁止依赖 engine），彻底消除 `engine↔service`
> 循环依赖。
