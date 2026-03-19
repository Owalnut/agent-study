# Walnut Agent

一个包含前端、后端、工作流执行能力的 AI Agent 项目，当前按以下约束实现：

- 后端：`JDK 17 + Spring Boot 3 + MyBatis-Plus + JWT + MySQL 8.0`
- 流程执行：同步执行，节点失败重试 3 次，节点超时 10 秒后中断
- 存储：工作流 JSON 入库，包含 `isDraft / isPublished`
- 音频输出：接口返回 `base64` 音频

## 目录结构

- `apps/web`: React 前端流图面板
- `apps/server`: Spring Boot 后端服务
- `packages/workflow-engine`: 预留工作流包（后续可并入 Java 引擎）

## 本地启动

前置条件：

- `JDK 17`
- `Maven 3.9+`
- `MySQL 8.0`（创建库：`walnut_agent`）

启动：

```bash
npm install
npm run dev
```

访问：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8787`

## 后端配置

配置文件：`apps/server/src/main/resources/application.yml`

- 默认数据库连接：`root/root@localhost:3306/walnut_agent`
- JWT 有效期：365 天
- SQL 初始化：`schema.sql` 自动执行

## 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 当前能力

- `POST /api/auth/login`：JWT 登录
- `GET /api/workflows/default`：获取默认工作流
- `POST /api/workflows`：保存工作流（含草稿/发布字段）
- `POST /api/workflows/debug`：执行调试，返回文本和音频 `base64`
- `GET /api/workflows/executions/{executionId}`：查询执行记录和结果

调试执行响应新增：

- `executionId`：执行记录 ID
- `executionStatus`：`SUCCESS / TIMEOUT / INTERRUPTED / FAILED`
- `errorCode`：例如 `NODE_TIMEOUT / NODE_INTERRUPTED / NODE_RETRY_EXHAUSTED`
