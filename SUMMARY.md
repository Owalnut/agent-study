# Walnut Agent 项目总结

## 1. 项目背景与目标

`walnut-agent` 是一个面向 AI 工作流编排与调试的单体项目，核心目标是实现一个典型的「Agent 流图执行面板」：

- 左侧提供节点库（大模型节点、工具节点）
- 中间提供可视化画板（输入节点 -> LLM 节点 -> TTS 节点 -> 输出节点）
- 右侧提供节点配置区域
- 底部提供调试抽屉，支持输入文本并执行工作流
- 执行完成后返回文本和音频，实现类似 AI 播客播放体验

项目当前定位为 MVP（最小可用版本），重点在于把“配置-执行-回放”主链路打通，并具备可持续演进的后端架构基础。

---

## 2. 当前技术架构总览

项目采用前后端分离，整体为本地单体部署：

- 前端：`React + Vite + React Flow`
- 后端：`JDK 17 + Spring Boot 3 + Spring Security + MyBatis-Plus + JWT`
- 数据库：`MySQL 8.0`
- 执行模式：同步执行
- 缓存 / MQ：当前不启用（按规划后续优化）

目录结构：

- `apps/web`：前端应用（可视化面板）
- `apps/server`：后端服务（认证、工作流存储、执行调试）
- `packages/workflow-engine`：早期 JS 引擎包（当前后端主逻辑已迁移到 Java）

---

## 3. 需求约束与决策记录（已确认）

以下是已与你确认并固化到实现中的关键决策：

- 后端技术栈：`Spring Boot 3`，JDK 改为 `17`
- 数据库：`MySQL 8.0`
- 缓存：暂不使用
- 消息队列：暂不使用
- 执行方式：同步
- 鉴权：`JWT`，单租户
- API 风格：`REST`（必要时可扩展 WebSocket）
- 音频返回：`base64`（方案 B）
- 工作流存储：`workflow_json` 入库
- 工作流版本字段：`is_draft` / `is_published`
- 失败策略：默认重试 `3` 次
- 超时策略：节点级 `10s` 超时并可中断，整体流程暂不设置总超时
- 部署方式：本地单体
- 任务执行流程：**规划 -> 执行 -> 验证 -> 完成**

---

## 4. 前端实现总结（apps/web）

### 4.1 页面结构

前端实现了 4 区域面板布局：

- 左侧：节点库
  - 大模型节点（如“通义千问”“DeepSeek”）
  - 工具节点（“超拟人音频合成”）
- 中间：React Flow 画板
  - 默认加载线性流程节点和边
- 右侧：节点配置展示
  - 点击节点可查看基础配置（ID、类型、名称）
- 底部：调试抽屉
  - 输入调试文本，调用后端执行接口
  - 自动播放返回音频（base64 data URL）

### 4.2 认证与调用链路

前端启动后会先调用：

1. `POST /api/auth/login` 获取 JWT
2. 使用 `Authorization: Bearer <token>` 调用工作流接口

主要调试链路：

- `GET /api/workflows/default` 拉取默认流程
- `POST /api/workflows/debug` 提交输入 + workflow 图结构
- 后端返回 `audioBase64` 后，前端用 `Audio()` 自动播放

---

## 5. 后端实现总结（apps/server）

### 5.1 架构分层

当前后端基本分为：

- Controller 层：认证与工作流 API
- Service 层：JWT、工作流执行、重试与超时控制
- Mapper 层：MyBatis-Plus 数据访问
- Entity/DTO 层：表映射与接口对象
- Security 层：JWT 过滤与鉴权策略

### 5.2 鉴权实现

核心组件：

- `JwtService`：签发/解析 token
- `JwtAuthFilter`：读取并校验 Bearer Token
- `SecurityConfig`：
  - 放行 `POST /api/auth/login`
  - 其他接口默认需认证
  - 无状态会话（`STATELESS`）

默认登录账号（当前实现）：

- username: `admin`
- password: `admin123`

JWT 有效期按配置为 365 天。

### 5.3 工作流执行实现

执行服务位于 `WorkflowService`，核心能力：

- DAG 拓扑排序执行
- 节点按类型分发（`input` / `llm` / `tool_tts` / `output`）
- 节点级超时控制（10 秒）
- 节点级失败重试（默认 3 次）
- 重试指数退避（可配置初始 backoff + multiplier）
- 执行状态落库与结果回传

当前状态码与错误码设计：

- `executionStatus`
  - `SUCCESS`
  - `TIMEOUT`
  - `INTERRUPTED`
  - `FAILED`
- `errorCode`
  - `NODE_TIMEOUT`
  - `NODE_INTERRUPTED`
  - `NODE_RETRY_EXHAUSTED`
  - `INTERNAL_ERROR`

### 5.4 关键接口清单

认证：

- `POST /api/auth/login`

工作流：

- `GET /api/workflows/default`：获取默认流程定义
- `POST /api/workflows`：保存工作流（含 draft/published 字段）
- `POST /api/workflows/debug`：执行调试
- `GET /api/workflows/executions/{executionId}`：查询执行结果

---

## 6. 数据库设计总结（MySQL 8.0）

### 6.1 workflow_definition

用于保存工作流定义：

- `id` 主键
- `name` 工作流名称
- `workflow_json` 工作流 JSON 内容
- `is_draft` 是否草稿
- `is_published` 是否发布
- `created_at` / `updated_at`

### 6.2 workflow_execution

用于记录调试执行情况：

- `id` 主键
- `workflow_id` 对应工作流 ID
- `input_text` 输入文本
- `status` 执行状态
- `output_text` 输出文本
- `audio_base64` 音频 base64
- `error_message` 错误信息
- `created_at` / `updated_at`

### 6.3 初始化策略

`schema.sql` 在启动时自动执行（`spring.sql.init.mode=always`），并通过 `DataInitializer` 在首次启动插入默认发布工作流。

---

## 7. 配置与运行方式

### 7.1 关键配置（application.yml）

- 服务端口：`8787`
- 数据库连接：`walnut_agent`
- JWT：
  - `secret`
  - `expiration-days: 365`
- 工作流执行：
  - `node-timeout-ms: 10000`
  - `retry-times: 3`
  - `retry-backoff-ms: 200`
  - `retry-backoff-multiplier: 2`

### 7.2 本地启动

根目录执行：

```bash
npm install
npm run dev
```

说明：

- 后端通过 Maven 启动 Spring Boot
- 前端通过 Vite 启动开发服务

访问地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8787`

---

## 8. 已完成能力与业务价值

当前版本已具备以下业务可用价值：

- 可视化工作流调试闭环已跑通（输入文本 -> 执行 -> 返回音频）
- 工作流配置可持久化（JSON 入库）
- 具备基本发布语义（草稿/发布）
- 执行可追踪（execution 记录 + 查询接口）
- 执行引擎具备最基础可靠性（重试、超时、中断状态）
- 安全层具备基本接入能力（JWT）

---

## 9. 当前限制与风险

MVP 阶段存在以下限制：

- 节点能力较简化，参数映射与多输入输出尚未完善
- LLM/TTS 厂商适配层仍需标准化（当前以模拟/简化逻辑为主）
- 执行模型为同步，复杂任务可能阻塞请求线程
- 未引入缓存、队列、分布式锁等高并发保障
- 观察性能力较弱（无完整链路日志、指标与追踪）
- 前端当前仍为 JS，尚未迁移为 `React + TypeScript`

---

## 10. 下一阶段建议（按优先级）

### P1（建议尽快）

- 前端迁移到 `React + TypeScript`
- 节点参数面板增强（模型、温度、音色、重试/超时覆盖）
- 调试面板展示节点级执行日志与耗时
- 增加执行重跑接口（按历史执行 ID 快速重放）

### P2（稳定性与可运维）

- 增加全局异常规范、统一错误码字典
- 增加执行链路追踪 ID（traceId）
- 新增基础监控指标（QPS、时延、失败率、超时率）
- 增加接口测试与服务层单元测试

### P3（扩展能力）

- 抽象多厂商模型网关（OpenAI/DeepSeek/通义千问）
- 增加异步执行模式（可选 MQ）
- 音频对象存储（MinIO/OSS）替代大体积 base64 入库策略
- 工作流版本管理（历史版本、回滚、对比）

---

## 11. 结论

本项目已经完成了从概念验证到可运行 MVP 的关键跨越：

- 产品交互层面形成了完整的 Agent 流图执行面板雏形
- 后端从 Node 迁移到 Spring 技术体系，符合既定企业级技术路线
- 数据、鉴权、执行、调试链路全部打通

在此基础上，后续可围绕「类型化前端、执行可靠性、供应商接入、可观测性」四个方向逐步演进到可生产化版本。
