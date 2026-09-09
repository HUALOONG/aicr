# AI 驱动代码评审与测试生成平台

通过 Webhook 自动捕获代码变更，利用 LLM 进行多维度智能评审并生成测试用例，将评审意见与测试用例回写至代码托管平台，并提供管理控制台。

## 文档索引

| 文档                                                                                             | 说明                                                      |
|:-------------------------------------------------------------------------------------------------|:----------------------------------------------------------|
| [`docs/requirements/软件需求说明书（SRS）.md`](docs/requirements/软件需求说明书（SRS）.md)       | 26 个功能需求、非功能需求、接口与数据需求                 |
| [`docs/requirements/概要设计说明书（HLD）.md`](docs/requirements/概要设计说明书（HLD）.md)       | 总体架构、9 个 Maven 模块划分、依赖红线                   |
| [`docs/requirements/详细设计说明书（LLD）.md`](docs/requirements/详细设计说明书（LLD）.md)       | 26 张表、状态机、Kafka 消息契约、异常处理                 |
| [`docs/requirements/Prompt（指令）设计说明书.md`](docs/requirements/Prompt（指令）设计说明书.md) | 指令模板、变量契约、输出 Schema、Token 预算与降级         |
| [`docs/requirements/前端详细设计说明书.md`](docs/requirements/前端详细设计说明书.md)             | 技术选型、路由与权限、21 个页面设计                       |
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml)                                                 | **98 个接口的字段级契约（唯一事实依据）**                 |
| [`docs/standards/开发分支管理规范.md`](docs/standards/开发分支管理规范.md)                       | master+develop 双主干、v0.1~v0.6 版本节奏、CI 门禁        |
| [`docs/standards/编码规范.md`](docs/standards/编码规范.md)               | 跨端契约：字段/时间数值/错误码/枚举/链路/幂等/兼容        |
| [`docs/standards/编码规范（后端 Java）.md`](docs/standards/编码规范（后端%20Java）.md)           | Java 分层/命名/事务/日志/Kafka/单测 + Spotless+Checkstyle |
| [`docs/standards/编码规范（前端 TS_Vue）.md`](docs/standards/编码规范（前端%20Vue）.md)          | Vue3 组件/样式/状态/接口/权限/错误/性能 + Oxlint+Oxfmt    |
| [`docs/plans/测试计划与策略.md`](docs/plans/测试计划与策略.md)                                   | 分层测试、性能/安全基线、重点场景、DoD                    |
| [`docs/standards/CI_CD 与环境规范.md`](docs/standards/CI_CD%20与环境规范.md)                     | 流水线、环境、制品、质量门禁、部署与回滚                  |

## 技术栈

| 层   | 选型                                                                            |
|:-----|:--------------------------------------------------------------------------------|
| 后端 | Spring Boot 4.1.x / Java 21 / MyBatis-Flex / Spring Kafka                       |
| 前端 | Vue 3 + TypeScript + Vite+（`vp` CLI）+ Tailwind CSS v4 + shadcn-vue（Reka UI） |
| 存储 | PostgreSQL 18+ / Redis 8.0+                                                     |
| 消息 | Kafka 4.3+                                                                      |
| LLM  | OpenAI API 兼容协议                                                             |

## 工程结构

```
aicr-parent                 父 POM（仅聚合 + dependencyManagement）
├- aicr-common             库：常量/枚举/统一返回/工具类（零框架依赖）
├- aicr-api                库：RPC DTO + facade 内部接口声明
├- aicr-security           库：认证/授权/加密/脱敏
├- aicr-base               库：持久化/MQ/全局配置/全局异常
├- aicr-service            库：业务逻辑
├- aicr-engine             库：分析引擎/回写/大变更/反馈闭环
├- aicr-web                可执行：控制台 RPC 入口    （:8080）
├- aicr-webhook            可执行：Webhook 回调接收   （:8081）
├- aicr-worker             可执行：Kafka 消费 + 引擎  （:8082）
└- aicr-console            前端工程（Vite）
```

### 依赖红线（构建期强制）

| 红线   | 约束                                                | 固化方式                  |
|:-------|:----------------------------------------------------|:--------------------------|
| 红线一 | `engine` 禁止依赖 `service`                         | Maven Enforcer + ArchUnit |
| 红线二 | `web` 禁止依赖 `engine`（engine 唯一入口是 worker） | Maven Enforcer + ArchUnit |
| 红线三 | `webhook` 禁止依赖 `service` / `engine`             | Maven Enforcer + ArchUnit |
| 红线四 | `common` 禁止依赖 Spring 与任何业务模块             | Maven Enforcer + ArchUnit |

任一违反将导致构建失败（测试见 `aicr-worker/src/test/java/com/joyintech/aicr/ArchitectureTest.java`）。

> **依赖红线状态（已一致）**：Webhook 解析策略置于 `aicr-webhook` 内部的 `...webhook.parser` 包（LLD §2.4.2 / §2.4.3），与 HLD
> §2.4.4 依赖矩阵"webhook 禁止依赖 engine"一致 —— 避免把 LLM/回写代码打进轻量回调服务，保障"独立扩容、P99 ≤200ms"目标。该约束由
> Maven Enforcer + ArchUnit 在构建期强制（LLD 附录 C）。

## 快速开始

### 1. 启动依赖中间件

> 以下为设计目标态。骨架阶段 Flyway `V1__init.sql`、`ArchitectureTest.java` 与 `aicr-console` 前端工程尚未入库，需按设计文档补齐。

按《HLD》§8.1 准备 **PostgreSQL 18+ / Redis 8.0+ / Kafka 4.3+**，并确保三者对 `aicr-web` / `aicr-webhook` / `aicr-worker` 三个部署单元网络可达（连接信息通过各单元配置文件或配置中心注入，SRS §4.4）。

数据库表结构由 **aicr-worker 启动时自动执行 Flyway 迁移**（`aicr-worker/src/main/resources/db/migration/V1__init.sql`
），web 与 webhook 的 Flyway 均已关闭（HLD §2.4.6）。

### 2. 构建后端

```bash
mvn -q -DskipTests clean install
```

### 3. 启动（顺序：worker → web → webhook）

```bash
java -jar aicr-worker/target/aicr-worker.jar
java -jar aicr-web/target/aicr-web.jar
java -jar aicr-webhook/target/aicr-webhook.jar
```

健康检查：`curl localhost:8080/actuator/health`

### 4. 启动前端

```bash
cd aicr-console
vp install      # 由 vp 调度 pnpm，依赖锁定于 pnpm-lock.yaml
vp dev          # http://localhost:5173，/api 代理至 :8080
```

### 5. 生成前端接口类型

```bash
npx @openapitools/openapi-generator-cli generate \
  -i ../docs/api/openapi.yaml -g typescript-axios -o src/api/types
```

## 关键约定

- **API 风格**：统一 RPC，`POST /api/v1/{module}/{action}`，业务参数置于 `param`，响应为 `{code, message, data, requestId}`
- **状态口径**：任务业务状态共 14 个（`review_task.sub_status`），主状态 5 个（`status`），映射见 `TaskSubStatus` 枚举
- **数据库迁移**：仅 worker 执行，禁止多单元并发上锁
- **数据权限**：由后端 SQL 层过滤，前端不拼数据范围参数
