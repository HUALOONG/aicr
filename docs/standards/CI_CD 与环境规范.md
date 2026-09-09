# AI 驱动代码评审与测试生成平台 — CI/CD 与环境规范

---

| 版本 | 日期       | 变更内容                                       | 变更人 |
|:-----|:-----------|:-----------------------------------------------|:-------|
| v1.0 | 2026-09-09 | 初始版本（流水线、环境、制品、质量门禁、部署） | 王飞   |

---

## 1 目的与范围

定义从代码提交到生产发布的持续集成与交付流程，固化《开发分支管理规范》§5 的 CI 门禁为可执行流水线。

- **分支模型**见《开发分支管理规范》（`master` + `develop` 双主干、短特性分支、`v0.1~v0.6` 内部版本、`v1.0.0` 冻结）；
- **编码规范**见《编码规范（后端 Java）》§7 与《编码规范（前端 TS/Vue）》§13；
- **测试策略**见《测试计划与策略》。

> 本文档以**阶段与门禁**为准，具体托管平台（Jenkins / GitLab CI / GitHub Actions）语法等价，附 GitLab CI 风格示例（§9），待平台确认后落地为 `.gitlab-ci.yml` 或对应文件。

---

## 2 流水线总览

| 触发                 | 阶段                                           | 产物     | 门禁         |
|:---------------------|:-----------------------------------------------|:---------|:-------------|
| PR → `develop`       | 构建 → 静态检查 → 单测 → 前端检查 → 契约校验   | 无发布物 | 全绿方可合并 |
| 合入 `develop`       | 构建 → 全量测试（含集成）→ 安全扫描            | 快照制品 | 失败告警     |
| PR → `master`        | 同 PR 校验 + Flyway 幂等 + 契约快照比对 + 冒烟 | —        | 全绿         |
| `master` 打 `tag v*` | 构建 → 发布（3 jar + 前端 dist）→ 部署         | 正式制品 | —            |
| `release/v1.0.0`     | 仅收 bugfix → 回归 → 合 `master`/`develop`     | 正式制品 | —            |

---

## 3 环境与配置

| 环境    | 用途              | 中间件来源                         | 配置注入                   |
|:--------|:------------------|:-----------------------------------|:---------------------------|
| dev     | 本地/开发自测     | 本地或 Testcontainers              | 本地 `application-dev.yml` |
| test    | PR 集成、每日构建 | Testcontainers 临时实例            | CI 变量                    |
| staging | E2E/性能/安全     | 环境提供（PG18+/Redis8/Kafka4.3+） | 配置中心 / CI 密文         |
| prod    | 生产              | 生产环境                           | 配置中心 / 运维注入        |

- **禁止**把 API 地址、密钥、Token 写入代码或配置文件入库（`SRS` §4.4）；统一经环境变量或配置中心注入；
- 三个部署单元（`aicr-web` / `aicr-webhook` / `aicr-worker`）共享同一 PostgreSQL / Redis / Kafka，连接信息不进仓；
- 敏感配置（accessToken / apiKey / webhookSecret / secret）运行时 AES-256 解密，CI 中仅以**密文变量**传递。

---

## 4 制品管理

- 版本号：父 POM `revision` 统一定义，**三个 jar 与前端共用同一版本**（如 `1.0.0`），禁止各单元单独发版；
- 后端制品：`mvn -B clean install` 产出 `aicr-web.jar` / `aicr-webhook.jar` / `aicr-worker.jar`；
- 前端制品：`vp build` 产出 `dist/`（CSS gzip ≤30KB、首屏 JS gzip ≤350KB 为体积门禁）；
- 制品推送至制品仓库（Nexus / Artifactory），`tag` 触发正式版本入库，快照仅保留最近 N 份；
- Flyway 迁移脚本随 `aicr-worker` 制品发布，由 worker 启动时自动执行（HLD §2.4.6）；
- **后端制品为瘦身 jar**（`spring-boot-maven-plugin` 排除全部依赖），部署目录固定为四件套：

  ```
  <APP_HOME>/
  ├─ aicr-web.jar          # 仅自身 class，Main-Class 为 PropertiesLauncher
  ├─ libs/                 # 全部运行时依赖（maven-dependency-plugin 产出）
  ├─ conf/                 # application.yml 等配置（maven-resources-plugin 产出）
  └─ bin/                  # 启停脚本（打包时从仓库根 bin/ 拷入）
     ├─ env.sh             #   公共环境：定位 jar、pid、日志、JVM 参数
     ├─ start.sh           #   后台启动
     ├─ stop.sh            #   优雅停止（TERM → 超时 kill -9）
     ├─ restart.sh         #   重启
     ├─ status.sh          #   运行状态（运行中 exit 0 / 已停止 exit 1）
     └─ start.cmd / stop.cmd    # 本地 Windows 自测用
  ```

  jar 内**不含**依赖与 `application.yml`，部署时按 §7 用 `bin/` 下脚本启停（脚本内已固化 `loader.path=libs,conf`）；
  运行期生成 `logs/`（日志）与 `run/`（pid 文件），均已加入 `.gitignore`。

---

## 5 流水线阶段定义

### 5.1 后端（每次 PR / 合并）

```bash
mvn -B -DskipTests=false clean verify
# 串联：spotless:check + checkstyle:check + ArchUnit + Enforcer + 单测 + 集成测试
mvn -B jacoco:report        # 覆盖率报告
mvn -B org.owasp:dependency-check-maven:check   # 漏洞扫描
```

### 5.2 前端（每次 PR / 合并）

```bash
vp install --frozen-lockfile
vp check                    # tsgo 类型检查 + Oxlint + Oxfmt 校验
vp test --run               # Vitest 单测/组件测
vp build                    # 产出 dist/，校验体积预算
pnpm audit                  # 依赖漏洞扫描
```

### 5.3 契约校验（PR 必过）

- 校验 Controller 入参/出参与 `../api/openapi.yaml` 一致（自研注解或 `openapi-generator` 生成类型比对）；
- 契约变更未同 PR 同步实现 → 失败（开发分支管理规范 §6.2）；
- `release` 阶段额外做**契约快照比对**，确保向后兼容（通用约定 §9）。

### 5.4 安全扫描（每日 / 发布前）

- 后端：`dependency-check`（阻断高危 CVE）；
- 前端：`pnpm audit`（阻断高危）；
- 密钥扫描：CI 中扫描待提交内容是否含硬编码密钥（如 `sk-`、`AKID`），命中即阻断。

---

## 6 质量门禁（阻断条件）

| 门禁                                   | 阈值                                     | 阶段         |
|:---------------------------------------|:-----------------------------------------|:-------------|
| 后端单测覆盖率                         | Jacoco **≥ 80%**                         | PR           |
| ArchUnit / Enforcer                    | 0 违反                                   | PR           |
| Checkstyle / Spotless / Oxlint / Oxfmt | 0 error                                  | PR           |
| 前端类型检查（tsgo）                   | 0 error                                  | PR           |
| 依赖漏洞                               | 0 高危 CVE                               | 每日 / 发布  |
| 契约一致性                             | 与 `openapi.yaml` 一致                   | PR / release |
| 前端体积                               | CSS gzip ≤30KB、首屏 JS gzip ≤350KB      | 构建         |
| 性能基线                               | §6（API≤2s、Webhook P99≤200ms、≥50 QPS） | 发布前       |

---

## 7 部署与回滚

- 构建成功后推送制品；生产部署由运维或发布流水线拉取制品启动；
- 启动顺序固定：**worker → web → webhook**（README 快速开始）；
- **启停方式（推荐）**：使用随制品分发的 `bin/` 脚本，脚本内已固化 `loader.path=libs,conf`，无需手工拼参数：

  ```bash
  # 在部署单元目录下执行（脚本自动探测本目录下的 *.jar）
  ./bin/start.sh                     # 后台启动，日志 logs/<app>.log，pid 写入 run/<app>.pid
  ./bin/status.sh                    # 运行中 exit 0，已停止 exit 1
  ./bin/stop.sh                      # TERM 优雅停止，超时（默认 30s）后 kill -9
  ./bin/restart.sh                   # stop + start

  JAVA_OPTS="-Xms1g -Xmx2g" ./bin/start.sh     # 覆盖 JVM 参数
  STOP_TIMEOUT=60 ./bin/stop.sh                # 调整优雅停止等待
  ```

  - 首次拉取制品后需 `chmod +x bin/*.sh`；Windows 本地自测可用 `bin\start.cmd` / `bin\stop.cmd`；
  - 按启动顺序执行：`cd aicr-worker && ./bin/start.sh` → `cd ../aicr-web && ./bin/start.sh` → `cd ../aicr-webhook && ./bin/start.sh`；
- **等价的手工命令**（脚本不可用时的兜底）：三个 jar 均为瘦身包（见 §4），必须显式指定 `loader.path`：

  ```bash
  # 工作目录含 aicr-xxx.jar + libs/ + conf/
  java -Dloader.path=libs,conf -jar aicr-worker.jar
  java -Dloader.path=libs,conf -jar aicr-web.jar
  java -Dloader.path=libs,conf -jar aicr-webhook.jar
  ```

  - `libs/`：全部运行时依赖；`conf/`：`application.yml` 等配置；二者经 `loader.path` 加入 classpath；
  - 等价写法：`LOADER_PATH=libs,conf java -jar aicr-web.jar`（环境变量，容器内更方便）；
  - 不带该参数直接 `java -jar` 会启动失败（依赖与配置都不在 classpath）；
- 健康检查：`curl localhost:8080/actuator/health`（web）、webhook 与 worker 对应端口；
- 回滚：保留最近 N 个版本制品，回滚即重新部署上一稳定 `tag`；数据库迁移**只向前**（先加列后删列，LLD §4），回滚前确认无破坏性迁移；
- 前端 `dist/` 静态托管，回滚即切换产物版本（前端设计说明书 §15.3）。

---

## 8 通知与可观测

- 构建失败、发布成功、安全扫描高危，通知至 `FR-020` 通知设置（企微/钉钉/飞书/邮件，SRS §3.7）；
- 流水线执行指标（时长、失败率）纳入 `FR-012` 监控看板，Webhook P99 / 队列深度 / LLM 成功率由 `MonitorMetricsData` 暴露。

---

## 9 平台落地示例（GitLab CI 风格，待平台确认）

```yaml
# .gitlab-ci.yml（骨架，需据实际平台调整）
stages: [build, check, test, security, package, deploy]

backend-check:
  stage: check
  script:
    - mvn -B verify
    - mvn -B jacoco:report

frontend-check:
  stage: check
  script:
    - vp install --frozen-lockfile
    - vp check
    - vp test --run

contract-check:
  stage: check
  script:
    - ./scripts/verify-openapi.sh   # 比对实现与 docs/api/openapi.yaml

security-scan:
  stage: security
  script:
    - mvn -B org.owasp:dependency-check-maven:check
    - pnpm audit

package:
  stage: package
  only: [tags]
  script:
    - mvn -B clean install
    - vp build
```

---

## 10 关联文档

- 《开发分支管理规范》—— 分支模型、§5 门禁矩阵、§9 分支保护
- 《编码规范（后端 Java）》§7 / 《编码规范（前端 TS/Vue）》§13 —— 格式与静态检查
- 《测试计划与策略》—— 测试分层、DoD、性能/安全基线
- `SRS` §4 非功能与安全、§5 接口；`HLD` §2.4.6 部署单元、§8 部署环境
- `../api/openapi.yaml` —— 契约校验基准
- 前端设计说明书 §15 构建与部署
