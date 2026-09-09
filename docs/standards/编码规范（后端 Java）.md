# AI 驱动代码评审与测试生成平台 — 编码规范（后端 Java）

---

| 版本 | 日期       | 变更内容                                                        | 变更人 |
|:-----|:-----------|:----------------------------------------------------------------|:-------|
| v1.0 | 2026-09-09 | 初始版本（Java 21 / Spring Boot 4.1.x / Spotless + Checkstyle） | 王飞   |

---

## 1 目的与适用范围

约束 9 个 Maven 模块（`common` / `api` / `security` / `base` / `service` / `engine` / `web` / `webhook` / `worker`）的
Java 编码实现。

- **跨端契约**（字段、错误码、枚举、链路、兼容策略）见《编码规范》，本文档不重复；
- **包结构与模块映射**、 **依赖红线**以 `LLD` §2.4 与 `HLD` §2.4.3 为准，本文档只写"怎么写"，不复制"怎么划分"；
- **流程与门禁**见《开发分支管理规范》。

技术基线：Java 21 · Spring Boot 4.1.x · MyBatis-Flex · Spring Kafka · Maven 多模块。

---

## 2 工程与模块规范

| 项       | 约定                                                                                           |
|:---------|:-----------------------------------------------------------------------------------------------|
| 基础包   | `com.joyintech.aicr.<module>.<layer>`                                                          |
| 版本管理 | 父 POM 用 `revision` 统一定义版本，子模块**禁止**自带 `<version>` 与 `<groupId>`               |
| 依赖声明 | 父 POM `dependencyManagement` 统一管版本；子模块只声明 `groupId:artifactId`                    |
| 打包     | 仅 `web` / `webhook` / `worker` 允许 `spring-boot-maven-plugin` repackage；库模块禁止          |
| 红线强制 | Maven Enforcer（禁依赖）+ ArchUnit（分层校验），任一违反构建失败（`HLD` §2.4.5、`LLD` 附录 C） |

**禁止事项（会被 ArchUnit/Enforcer 拦截）**：

- `engine` 依赖 `service`；`web` 依赖 `engine`；`webhook` 依赖 `service` / `engine`；
- `common` 引入 Spring 或任何业务模块；
- 在 `service` 中 `new` 或调用 `engine` 内部类触发分析（触发一律走 Kafka）。

---

## 3 命名规范

### 3.1 类与接口

| 类型         | 命名                                                               | 位置                                      |
|:-------------|:-------------------------------------------------------------------|:------------------------------------------|
| 控制器       | `XxxController`                                                    | `web.controller.*` / `webhook.controller` |
| 业务服务     | `XxxService`（**单类，不强制接口 + Impl**）                        | `service.*`                               |
| 数据访问     | `XxxRepository` / `XxxMapper`                                      | `base.repository`                         |
| **请求 DTO** | `XxxParam`                                                         | `api.dto`                                 |
| **响应 DTO** | `XxxData`                                                          | `api.dto`                                 |
| 领域实体     | `Xxx`（与表名对应，如 `ReviewTask`）                               | `base.repository.entity` 或 domain 包     |
| 枚举         | 直接用语义名（`SubStatus`、`Severity`）                            | `common.enums`                            |
| 异常         | `XxxException`（继承 `BusinessException` 或 `RuntimeException`）   | `base.exception` / `common.exception`     |
| 配置属性     | `XxxProperties`                                                    | `base.config`                             |
| Kafka 监听   | `XxxListener`                                                      | `worker`                                  |
| 引擎组件     | `AnalysisEngineService`、`ResultWriteBackService` 等（`LLD` §3.7） | `engine.*`                                |

> **DTO 命名必须与 `openapi.yaml` 的 schema 同名**：契约里的 `DashboardSummaryParam` / `DashboardSummaryData`
> 在代码中即为同名类，便于契约与实现比对。`api` 模块只放 DTO 与 facade 接口声明， **禁止**放 Controller/Service 实现与业务逻辑（
> `HLD` §2.4.2）。

### 3.2 方法命名

与 RPC action 保持一致：`list` / `getById` / `create` / `update` / `delete` / `toggleStatus` / `apply` / `trigger` /
`retry` / `export`。
持久化层：`selectXxx` / `insertXxx` / `updateXxx` / `deleteXxx`（MyBatis-Flex 风格）。

### 3.3 其它

- 常量：`UPPER_SNAKE_CASE`，集中定义在 `common.constant`， **禁止魔法值散落**（阈值如 Token 8000 / 1200、大变更 1000 / 2000 /
  20 / 5MB 必须常量化）；
- 布尔变量/字段不加 `is` 前缀（JavaBean 规范），JSON 序列化以 `openapi.yaml` 为准（通用约定 §4.1）；
- 包名全小写、单数形式，禁止下划线与复数混用。

---

## 4 分层职责

| 层           | 允许                                         | 禁止                                                 |
|:-------------|:---------------------------------------------|:-----------------------------------------------------|
| `Controller` | 参数校验（`@Valid`）、调用 Service、组装响应 | 写业务逻辑、直接调 Mapper、跨模块调用                |
| `Service`    | 业务编排、事务边界、发 Kafka 消息            | 直接调 `engine` 内部类、写 SQL                       |
| `Repository` | 单表/联表查询、分页、逻辑删除                | 业务判断、事务注解                                   |
| `engine`     | LLM 编排、回写、大变更、反馈闭环             | 依赖 `service`（配置/计费/通知经 `api.facade` 接口） |
| `worker`     | `@KafkaListener` 接收并转发给 engine         | 写业务逻辑、暴露 Web 控制器                          |
| `common`     | 常量、枚举、工具类、统一返回                 | 引入 Spring、依赖任何业务模块                        |

---

## 5 编码约定

### 5.1 依赖注入与配置

- **构造器注入**（`final` 字段 + Lombok `@RequiredArgsConstructor`）， **禁止字段 `@Autowired`**；
- 配置用 `@ConfigurationProperties` 集中管理，少量简单值可用 `@Value`；
- 密钥、Token **禁止**写进配置文件入库，一律走环境变量或配置中心（`SRS` §4.4）。

### 5.2 事务

- `@Transactional` **只标注在 Service 方法**上，默认 `REQUIRED`；
- **禁止在事务内发起远程调用**（LLM、代码平台出站、Kafka 发送）；
- 与 Kafka 的一致性采用"**先落库、后发消息 + 消费端幂等**"（`LLD` §10.4），不使用分布式事务；
- 只读方法标注 `@Transactional(readOnly = true)`。

### 5.3 异常处理（`LLD` §8.1）

| 异常                          | 处理                           |
|:------------------------------|:-------------------------------|
| `BusinessException`           | → `code=40001`，`message` 透传 |
| `LLMTimeoutException`         | → 触发重试或置 `RETRYING`      |
| `LLMUnavailableException`     | → 降级，置 `DEGRADED`          |
| `PlatformConnectionException` | → 置 `FAILED` 并记录           |
| 未捕获异常                    | → 全局处理器返回 `50001`       |

- **禁止吞异常**：catch 后必须记录日志或转译抛出，禁止空 `catch`；
- 禁止用异常控制正常流程；
- 错误码一律取《编码规范》§5 的取值，禁止自定义新码。

### 5.4 日志（`LLD` §8.2）

- 结构化 JSON 日志，必含 `traceId`、`requestId`、`userId`、`耗时`；
- `traceId` 从 `X-Trace-Id` 头或 MDC 获取， **无则生成**（通用约定 §7.1）；
- 日志落盘前 **再次脱敏**，禁止打印 `accessToken` / `apiKey` / `webhookSecret` / 完整凭证；
- 禁止 `System.out` / `System.err` / `printStackTrace`（Checkstyle 拦截）；
- 级别：ERROR 需人工介入；WARN 为可自动恢复；INFO 记录状态流转与关键链路；DEBUG 仅在排障期开启。

### 5.5 数据访问（MyBatis-Flex）

- 全部使用参数化查询， **禁止字符串拼接 SQL**（`SRS` §4.2）；
- 禁止 `SELECT *`，显式列字段；
- 逻辑删除统一用 `deleted` 列，查询条件由框架或手写 `AND deleted = 0`；
- 时间列统一 `TIMESTAMPTZ`（`LLD` §4）；
- 分页参数与通用约定 §3 一致（`page` / `size`，`size` ≤ 100）。

### 5.6 Kafka（`LLD` §10）

- 消息体固定为 `{msgId, traceId, eventType, occurredAt, payload}`；
- 生产者为 `service`（触发/重试/反馈）与 `webhook`（入队）， **消费者只能是 `worker`**；
- 消费端必须做幂等（`aicr:mq:consumed:{msgId}`），终态消息直接丢弃；
- 监听器 **禁止**抛出未捕获异常导致无限重投，失败应记录并交由重试/DLQ 机制；
- 通知类失败只记日志，不进 DLQ。

### 5.7 并发与锁

- 同仓库串行、跨仓库并行：Redis 分布式锁 `aicr:lock:repo:{repoId}`， **不设 TTL**，依赖 Redisson watchdog 续期（`LLD` §2.3，
  `volatile-lru` 下不会被淘汰）；
- 长耗时任务只能在 `worker` 执行，禁止占用 `web` 的 HTTP 线程池；
- 自定义线程池必须显式命名线程与队列容量，禁止无界队列。

### 5.8 安全与脱敏

- 敏感配置落库前 AES-256 加密（`LLD` §7.4）；
- 送 LLM 前必须调用 `SensitiveDataMasker`（`LLD` §7.3），调用点在渲染之后、发送之前；
- 数据权限由拦截器/切面统一注入， **禁止**在业务代码里手写 `deptId` 过滤（通用约定 §8）；
- Webhook 签名校验、IP 黑名单实现位于 `security.webhook`，不在业务模块重写。

---

## 6 单元测试

| 项       | 约定                                                                          |
|:---------|:------------------------------------------------------------------------------|
| 框架     | JUnit 5 + Mockito + AssertJ                                                   |
| 覆盖率   | 整体 ≥ 80%（`SRS` §4.4）；`engine` 渲染/裁剪/状态机、`service` 权限过滤为必测 |
| 命名     | `XxxServiceTest`；方法用 `@DisplayName` 中文描述"给定-当-那么"                |
| LLM 调用 | **必须 Mock**，禁止单测触发真实 LLM 或真实外部平台调用                        |
| 架构测试 | `ArchitectureTest`（ArchUnit）为**必写**，红线变更需同步更新                  |
| 测试数据 | 使用 Flyway + 测试容器或独立测试库，禁止依赖生产数据                          |

---

## 7 格式化与静态检查（Spotless + Checkstyle）

### 7.1 分工

| 工具           | 职责                                                               | 阶段                                   | 本地命令               |
|:---------------|:-------------------------------------------------------------------|:---------------------------------------|:-----------------------|
| **Spotless**   | 只管**格式**：缩进、换行、import 顺序、去除未用 import、license 头 | `check`（绑定 `validate` 或 `verify`） | `mvn spotless:apply`   |
| **Checkstyle** | 只管**规则**：命名、复杂度、禁 API、空 catch、行宽、魔法值等       | `check`（PR 必过）                     | `mvn checkstyle:check` |

两者 **规则不重叠**：凡是"能自动修复的排版问题"归 Spotless；凡是"需要人改的语义/结构问题"归 Checkstyle。

### 7.2 配置位置

```
build-config/
├── checkstyle.xml          # Checkstyle 规则集（全模块共用）
├── spotless-importorder    # import 顺序：java|javax|jakarta|org|com|其他
└── license-header.txt      # 版权头模板（可选）
```

父 POM 用 `pluginManagement` 统一声明版本，各模块仅声明插件执行，避免版本漂移。

### 7.3 关键规则（Checkstyle）

| 类别   | 规则                                                                                                                              | 级别          |
|:-------|:----------------------------------------------------------------------------------------------------------------------------------|:--------------|
| 基础   | `LineLength` ≤ **120**（中文与 URL 可放宽）                                                                                       | error         |
| 结构   | `NeedBraces`、`LeftCurly`、`RightCurly`、`EmptyStatement`                                                                         | error         |
| 命名   | `PackageName`、`TypeName`、`MethodName`、`MemberName`、`ParameterName`、`LocalVariableName`、`ConstantName`、`StaticVariableName` | error         |
| 导入   | `UnusedImports`、`RedundantImport`、`IllegalImport`（禁 `sun.*`、禁 `lombok.experimental` 未批准项）                              | error         |
| 编码   | `EmptyCatchBlock`、`IllegalCatch`（禁捕获 `Throwable`/`Error`）、`MissingSwitchDefault`、`FallThrough`、`EqualsHashCode`          | error         |
| 复杂度 | `CyclomaticComplexity` ≤ 12、`MethodLength` ≤ 80、`ClassFanOutComplexity` ≤ 25                                                    | error         |
| 设计   | `FinalClass`、`HideUtilityClassConstructor`（工具类必须私有构造）、`InterfaceIsType`                                              | error         |
| 禁用   | `Regexp` 禁 `System.out` / `System.err` / `printStackTrace`                                                                       | error         |
| 魔法值 | 阈值类常量（Token 8000/1200、大变更 1000/2000/20/5MB、重试 60s/3 次）**必须常量化**，由 `ConstantName` + 评审把关                 | warn → 评审拦 |

> **不做**的规则：`MagicNumber` 全局开启会产生大量误报（如 HTTP 200、分页 20），故改为"阈值常量化 + 评审把关"；`JavadocMethod`
> 不强制，但公共 API 与 facade 接口必须写。

### 7.4 CI 门禁

PR 阶段必跑（与《开发分支管理规范》§5 一致）：

```bash
mvn -B verify     # 含 spotless:check + checkstyle:check + ArchUnit + Enforcer + 单测
```

- 任一失败 **阻断合并**；
- 开发者提交前先跑 `mvn spotless:apply` 自动修复格式，再本地 `mvn verify`；
- IDE 需导入 `build-config/checkstyle.xml` 并安装 Spotless 对应格式化插件，保证"本地与 CI 结果一致"。

> 版本建议（以 Maven 中央仓库最新稳定版为准）：`spotless-maven-plugin`、`palantir-java-format` 或 `google-java-format`（均需支持
> JDK 21）、`maven-checkstyle-plugin` + `checkstyle` 10.x。

---

## 8 评审与提交

- 提交信息遵循《开发分支管理规范》§7：`feat(engine): ... (FR-023)`；
- PR 检查清单按《开发分支管理规范》§8 逐项确认（红线、Flyway、openapi、跨文档同步）；
- DTO 变更必须附 `openapi.yaml` 同名 schema 的变更；
- 新增枚举/状态必须同步：数据库列注释、`common.enums`、`openapi.yaml`、前端字典（通用约定 §6）。

---

## 9 关联文档

- 《编码规范》—— 跨端契约（字段、错误码、枚举、链路、幂等、兼容）
- `LLD` —— §2.4 包结构、§3 模块设计、§4 数据库、§6 状态机、§7 安全与权限、§8 异常与日志、§10 Kafka、附录 C 红线固化
- `HLD` —— §2.4 模块与依赖矩阵、§2.4.5 约束固化、§2.4.6 部署单元
- `SRS` —— §4 非功能与安全需求、§3 业务规则（BR 编号）
- 《开发分支管理规范》—— 分支、CI 门禁、PR 清单
