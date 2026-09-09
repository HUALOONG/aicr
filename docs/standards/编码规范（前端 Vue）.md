# AI 驱动代码评审与测试生成平台 — 编码规范（前端 TS/Vue）

---

| 版本 | 日期       | 变更内容                                            | 变更人 |
|:-----|:-----------|:----------------------------------------------------|:-------|
| v1.0 | 2026-09-09 | 初始版本（Vue 3 + TS + Tailwind v4 + Oxlint/Oxfmt） | 王飞   |

---

## 1 目的与适用范围

约束 `aicr-console` 前端工程（`Vue 3` + `TypeScript` + `Vite+` + `Tailwind CSS v4` + `shadcn-vue`）。

- **跨端契约**（字段、错误码、枚举、链路、兼容策略）见《编码规范》，本文档不重复；
- **工程结构、Tailwind 约定、接口层、状态管理、组件、性能、安全、测试**以《前端详细设计说明书》为准，本文档只写"约束与红线"，
  **不复制设计内容**；
- **流程与门禁**见《开发分支管理规范》。

---

## 2 权威引用与禁止复制

| 主题           | 权威文档章节                  | 本文档写法                   |
|:---------------|:------------------------------|:-----------------------------|
| 目录结构       | 前端说明书 §3                 | 引用，不重列目录树           |
| 样式约定       | 前端说明书 §2.4               | 引用，只写红线               |
| 接口封装       | 前端说明书 §6                 | 引用 `rpc()`，禁止散调       |
| 状态字典       | 前端说明书 §7（含 §7.3 兜底） | 引用，固化兜底调用           |
| 权限           | 前端说明书 §5                 | 引用，明确"数据权限不在前端" |
| 错误与全局交互 | 前端说明书 §12                | 引用，固化错误码处理         |
| 安全           | 前端说明书 §13                | 引用，固化 XSS/样式注入      |
| 测试           | 前端说明书 §14                | 引用，固化测试分层           |
| 性能           | 前端说明书 §11                | 引用，固化体积预算           |

> 设计文档改了，本文档同步引用即可； **禁止在两份文档里各写一份相同内容**，否则会像此前多份文档那样产生口径漂移。

---

## 3 工程与目录

- 工程根 `aicr-console/`，由 `Vite+`（命令 `vp`）统一管理依赖与脚本；
- 目录结构、组件层（shadcn-vue `ui/`）、stores/composables 组织见前端说明书 §3， **新增文件必须归入对应目录，禁止在 `src/`
  根散落**；
- 组件来源：`src/components/ui/` 由 `shadcn-vue` CLI 生成并纳入版本管理，业务改动以 diff 合并，不整体覆盖（前端说明书
  §2.4.4）；
- 环境配置经 `.env`（或配置中心）注入， **禁止**把 API 地址、密钥写死在源码（通用约定 §8）。

---

## 4 命名规范

| 对象           | 命名                            | 示例                                 |
|:---------------|:--------------------------------|:-------------------------------------|
| 页面/组件文件  | `PascalCase.vue`                | `ReviewReport.vue` / `StatusTag.vue` |
| 组合式函数     | `useXxx`（驼峰）                | `useReviewList`、`useDict`           |
| Pinia store    | `xxx.ts`（与 store 名一致）     | `user.ts` / `dict.ts` / `app.ts`     |
| 接口模块       | `modules/<module>.ts`           | `api/modules/review.ts`              |
| 常量/枚举映射  | `UPPER_SNAKE_CASE` 或 `XXX_MAP` | `SUB_STATUS_MAP`、`DIMENSION_MAP`    |
| 类型/接口      | `PascalCase`                    | `ReviewListParam`、`ReportData`      |
| CSS 类（业务） | kebab-case，走语义 token        | `review-toolbar`                     |

- 中文文案直接写在模板，不强求 i18n（面向内部平台）；但 **枚举 label、状态文案以 `dict` store 映射为准**
  ，禁止在模板硬编码枚举中文（避免新增枚举时改 N 处）。

---

## 5 组件规范

### 5.1 组件形态

- 一律 `<script setup lang="ts">`；`props` / `emits` **必须类型化**，禁用 `Object` / `any`：

```ts
const props = defineProps<{ taskId: number; status: SubStatus }>()
const emit = defineEmits<{ (e: 'confirm', id: number): void }>()
// 双向绑定用 defineModel（Vue 3.4+）
const visible = defineModel<boolean>('visible')
```

- 禁止在 `<template>` 内写复杂逻辑（循环 + 条件 + 三元嵌套），抽到 `computed` / `composable`；
- 组件按"展示型 / 容器型"分离：页面（`.vue` 路由组件）负责取数与编排，子组件只接收 props 渲染。

### 5.2 样式（高优先级红线，对齐前端说明书 §2.4）

| 红线               | 说明                                                                                                                                 |
|:-------------------|:-------------------------------------------------------------------------------------------------------------------------------------|
| **语义色优先**     | 业务色一律用 `bg-success/10 text-success` 等语义 token，**禁止**直接写 `text-red-500`、`bg-[#ff4d4f]`                                |
| **变体用 `cva()`** | 所有带状态/尺寸的组件变体用 `cva()` 统一管理，禁止模板里散落条件类名                                                                 |
| **动态类名白名单** | 字典色等拼接类名必须走 `@source inline("bg-{muted,success,...}")`，**禁止**用用户输入拼接类名（防样式注入，前端说明书 §2.4.2 / §13） |
| **暗色实现**       | 以 CSS 变量 + `.dark` 类为主，组件只引用语义 token，不写 `dark:` 分支                                                                |
| **`v-html` 限制**  | 除 Diff/代码渲染（依赖 diff2html 内置转义）外**禁用 `v-html`**（防 XSS，前端说明书 §13）                                             |

### 5.3 关键组件约定

- `DiffView`（`★核心`）：diff 文本由后端 `diffText` 字段提供，前端用 `diff2html` 渲染 + 行号定位（前端说明书 §10.1），不在前端重新计算
  diff；
- `StatusTag`：颜色经 §7.3 的 `getStatusMeta` 兜底，未知状态显示 muted 而非报错；
- `PermissionButton`：按钮级权限统一用它 + `v-permission` 指令（前端说明书 §5.2 / §10.4），禁止在模板里 `v-if="hasRole()"`。

---

## 6 状态管理（对齐前端说明书 §7）

| Store  | 职责                              | 持久化                                  |
|:-------|:----------------------------------|:----------------------------------------|
| `user` | 用户/角色/权限/菜单/token         | token 存 `localStorage`，刷新后重新拉取 |
| `dict` | 枚举字典与中文+样式映射（含兜底） | 否                                      |
| `app`  | 主题/折叠/紧凑/全局 loading       | 是                                      |

**红线**：

- **页面数据不进 Pinia**，一律在页面内（`ref`/`computed`）维护，避免全局状态膨胀与过期（前端说明书 §7）；
- **枚举字典访问必须走兜底封装**：`getStatusMeta(key)` 内部 `?? fallback`，禁止 `SUB_STATUS_MAP[key]` 直接解构（兜底规则见前端说明书
  §7.3、通用约定 §6.3）；
- 数据权限 **不在前端**实现，前端不拼接 `deptId` / `authorId` 等范围参数（通用约定 §8 / §10）。

---

## 7 接口层（对齐前端说明书 §6）

- 所有请求走统一 `rpc<TReq, TRes>(module, action, param)`，由请求拦截器注入 `Authorization`、`X-Trace-Id`、`requestId`
  （前端说明书 §6.2）；
- **禁止**在业务代码中直接 `axios` / `fetch` 散调；新增接口只扩 `api/modules/<module>.ts`；
- **TS 类型由 `openapi.yaml` 生成到 `api/types/`**（只读）， **禁止手改**；字段命名以契约为准（`lowerCamelCase`）；
- 业务异常统一抛 `BizError(code, message)`，由响应拦截器转全局 Toast（前端说明书 §12）；
- **重试语义**：仅 `50001` / `50002` 提供用户可见重试；写操作重试前先查幂等状态， **禁止盲目重放**（通用约定 §5.3 / §7.2）；
- 时间/数值/百分比 **在前端格式化展示**，服务端只出 ISO-8601 / 数值（通用约定 §4.2 / §10）：金额 `¥{x.toFixed(2)}`、百分比
  `{v}%`、时间 `dayjs` 本地化。

---

## 8 路由与权限（对齐前端说明书 §4 / §5）

- 菜单与路由由后端返回（前端说明书 §4.2）， **禁止**在前端写死全部路由与菜单树；
- 路由组件 **懒加载**（`() => import()`），控制首屏体积（前端说明书 §11）；
- 按钮级权限用 `v-permission` / `<PermissionButton>`，菜单级/数据级由后端保证。

---

## 9 错误处理与全局交互（对齐前端说明书 §12）

- 错误码处理严格按 §12 表格：`40001` 用 `data.detail` 回填 `FormField`、`40101` 清 token 跳登录、`40301` 提示无权限、`50001`
  携 `requestId` 提示、`50002` 提供重试；
- **禁止**静默 `catch`（空 `catch` 或 `catch (e) {}`），异常必须 toast 或 log；
- 全局仅 `App.vue` 挂载一个 `<Toaster rich-colors />`，禁止局部重复挂载；
- 破坏性操作统一 `AlertDialog` 二次确认；写操作成功 `toast.success` + 刷新列表。

---

## 10 性能（对齐前端说明书 §11）

| 项       | 约束                                                                  |
|:---------|:----------------------------------------------------------------------|
| 体积预算 | `vp build` 后 CSS（gzip）≤ 30KB、首屏 JS（gzip）≤ 350KB               |
| 路由     | 路由级 `() => import()` 懒加载                                        |
| 大列表   | 单页 >100 行启用 `@tanstack/vue-virtual` 虚拟滚动                     |
| 图表     | `ECharts` / `G6` 仅 `"!loading && !empty"` 时懒挂载，避免容器未尺寸化 |
| 资源     | 图标具名导入（`lucide-vue-next`），禁止 `import * as Icons`           |
| 加载态   | 表格查询用骨架行，禁止全屏 loading                                    |

---

## 11 安全（对齐前端说明书 §13）

- XSS：除 Diff 渲染外禁用 `v-html`；Diff 输出依赖 diff2html 内置转义；
- 样式注入：禁止用户输入拼接 Tailwind 类名，动态类走白名单 + `@source inline()`；
- Token 存 `localStorage`，登出 / `40101` 清除；
- 敏感字段（Access Token / API Key / 密钥）前端 **不缓存、不写日志**，表单留空表示不修改；
- 依赖安全：CI 执行 `vp install --frozen-lockfile` + `pnpm audit`，锁定 `pnpm-lock.yaml`。

---

## 12 测试（对齐前端说明书 §14）

| 类型      | 工具                       | 范围                                                                                                                        |
|:----------|:---------------------------|:----------------------------------------------------------------------------------------------------------------------------|
| 单元/组件 | `vp test`（Vitest + VTU）  | 工具函数（状态映射、`cn()`、`getStatusMeta` 兜底）、`StatusTag`/`PermissionButton`/`ChartCard` 三态、`DataTable` 分页与空态 |
| 类型      | `vp check`（tsgo）         | 全量 TS 类型 + `openapi` 生成类型与 props 一致性                                                                            |
| 规范      | `vp check`（Oxlint/Oxfmt） | 提交前 husky + lint-staged 触发                                                                                             |
| E2E       | Playwright                 | 登录 → 任务列表 → 报告 → 确认意见；权限按钮可见性                                                                           |

- **必须覆盖**：`getStatusMeta` 未知枚举兜底、表单 `detail` 回填、错误码分支、`rpc()` 重试与异常路径；
- **禁止**做样式快照测试（Tailwind 类名易变），改为行为断言。

---

## 13 格式化与静态检查（Oxfmt + Oxlint）

### 13.1 分工（与后端 Spotless + Checkstyle 对等）

| 工具       | 职责                                                             | 本地命令                               |
|:-----------|:-----------------------------------------------------------------|:---------------------------------------|
| **Oxfmt**  | 只管**格式**：缩进、引号、分号、import 排序、尾逗号              | `vp check --fix`（或 `oxfmt --write`） |
| **Oxlint** | 只管**规则**：类型安全、未捕获 Promise、未用变量、命名、禁止散调 | `vp check`（含 tsgo 类型检查）         |

两者 **不重叠**：排版问题归 Oxfmt（可自动 fix），语义/结构问题归 Oxlint（阻断提交）。

### 13.2 关键规则（Oxlint，以官方 `recommended` 为基，叠加以下）

| 类别     | 规则精神                                                                                       | 级别  |
|:---------|:-----------------------------------------------------------------------------------------------|:------|
| 类型安全 | 尽量避免 `any`；`any` 仅允许在与 openapi 生成类型边界处，且需 `// oxlint-disable` 注明         | error |
| Promise  | `no-floating-promises`：未 await 的 Promise 报错；`no-misused-promises`：禁止把 Promise 当值用 | error |
| 空值     | `no-unused-vars`、`no-undef`、`prefer-const`                                                   | error |
| 比较     | `eqeqeq`（允许 `== null` 判空）                                                                | error |
| 引用     | `no-explicit-any` 限定（见上）、`no-var`、禁止 `require`                                       | error |
| Vue      | `vue/no-mutating-props`、**禁止**模板散落复杂表达式、`vue/require-typed-ref`（ref 需标注类型） | error |
| 禁止     | `no-console`（生产构建）、`no-debugger`、`no-v-html`（仅在 Diff 渲染处白名单放行）             | error |
| 样式     | 配合 §5.2 红线，CI 额外扫描是否出现硬编码色号/拼接类名（可用正则表达式规则）                   | error |

### 13.3 CI 门禁（与《开发分支管理规范》§5 一致）

PR 阶段必跑：

```bash
vp check          # 类型检查(tsgo) + Oxlint + Oxfmt 校验
vp test --run     # Vitest 单测/组件测
```

- 任一失败 **阻断合并**；
- 提交前 husky + lint-staged 自动 `vp check --fix`，保证本地与 CI 一致；
- 配置位置：`oxlint.json` / `tsconfig.json`（`strict: true`）由 `Vite+` 脚手架统一管理，禁止在子目录重复覆盖。

---

## 14 提交与评审

- 提交信息遵循《开发分支管理规范》§7：`feat(console): ... (FR-005)`；
- PR 检查清单按《开发分支管理规范》§8：含 `openapi.yaml` 同 PR、跨文档同步、未引入二期项；
- 新增枚举/状态必须同步：`openapi.yaml`、`dict` store 映射、后端 `common.enums`、数据库注释（通用约定 §6）。

---

## 15 关联文档

- 《编码规范》—— 跨端契约（字段、错误码、枚举、链路、幂等、兼容）
- 《编码规范（后端 Java）》—— 后端分层/命名/事务/日志/Kafka/单测 + Spotless + Checkstyle
- `前端详细设计说明书` —— §2.4 样式、§3 结构、§4 路由、§5 权限、§6 接口层、§7 状态（含 §7.3 兜底）、§10 组件、§11 性能、§12 错误、§13
  安全、§14 测试
- `../api/openapi.yaml` —— 字段级契约（98 接口，类型生成唯一来源）
- 《开发分支管理规范》—— 分支、CI 门禁、PR 清单
