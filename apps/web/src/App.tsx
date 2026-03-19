import { DragEvent, useEffect, useMemo, useRef, useState } from "react";
import ReactFlow, { Background, Controls, Edge, Node, ReactFlowInstance } from "reactflow";
import "reactflow/dist/style.css";

const API_BASE = "http://localhost:8787";
const WS_BASE = "ws://localhost:8787/ws/debug";

type NodeType = "input" | "llm" | "tool_tts" | "output";
type WorkflowNodeMeta = { id: string; type: NodeType; data?: { name?: string; model?: string; voice?: string } };
type WorkflowEdge = { id: string; source: string; target: string };
type Workflow = { nodes: WorkflowNodeMeta[]; edges: WorkflowEdge[] };
type DebugOutput = { text?: string; audioBase64?: string; contentType?: string };
type WsEvent = { type: string; executionId?: number; payload?: Record<string, unknown> };

type FlowNode = Node<{ label: string }, "default"> & { meta: WorkflowNodeMeta };

const llmNodes: Array<{ type: NodeType; label: string; icon: string }> = [
  { type: "llm", label: "DeepSeek", icon: "🧠" },
  { type: "llm", label: "通义千问", icon: "✨" },
  { type: "llm", label: "AI Ping", icon: "🚀" },
  { type: "llm", label: "智谱", icon: "🧝" }
];
const toolNodes: Array<{ type: NodeType; label: string; icon: string }> = [
  { type: "tool_tts", label: "超拟人音频合成", icon: "🎙️" }
];

const nodeTypeLabel: Record<NodeType, string> = {
  input: "输入",
  llm: "大模型",
  tool_tts: "工具",
  output: "输出"
};

export default function App() {
  const [token, setToken] = useState("");
  const [nodes, setNodes] = useState<FlowNode[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [selectedNode, setSelectedNode] = useState<WorkflowNodeMeta | null>(null);
  const [debugOpen, setDebugOpen] = useState(true);
  const [debugInput, setDebugInput] = useState("你好，帮我生成一期关于 AI Agent 的播客开场白。");
  const [debugResult, setDebugResult] = useState<DebugOutput | null>(null);
  const [loading, setLoading] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const flowRef = useRef<ReactFlowInstance | null>(null);

  useEffect(() => {
    void loginAndLoad();
    return () => wsRef.current?.close();
  }, []);

  async function loginAndLoad(): Promise<void> {
    const loginResp = await fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "admin", password: "admin123" })
    });
    const loginData = (await loginResp.json()) as { token: string };
    const nextToken = loginData.token;
    setToken(nextToken);

    const workflowResp = await fetch(`${API_BASE}/api/workflows/default`, {
      headers: { Authorization: `Bearer ${nextToken}` }
    });
    const workflow = (await workflowResp.json()) as Workflow;
    const flowNodes = workflow.nodes.map((node, index) => ({
      id: node.id,
      type: "default" as const,
      position: { x: 280, y: 60 + index * 125 },
      data: { label: node.data?.name || nodeTypeLabel[node.type] || node.type },
      meta: node
    }));
    const flowEdges = workflow.edges.map((edge) => ({
      ...edge,
      animated: true
    }));
    setNodes(flowNodes);
    setEdges(flowEdges);
  }

  const workflowPayload = useMemo(
    () => ({
      nodes: nodes.map((node) => node.meta),
      edges: edges.map(({ id, source, target }) => ({ id, source, target }))
    }),
    [nodes, edges]
  );

  async function handleDebug(): Promise<void> {
    if (!token) return;
    setLoading(true);
    setLogs([]);
    setDebugResult(null);
    wsRef.current?.close();

    const ws = new WebSocket(`${WS_BASE}?token=${encodeURIComponent(token)}`);
    wsRef.current = ws;
    ws.onopen = () => {
      ws.send(
        JSON.stringify({
          type: "START_DEBUG",
          payload: { input: debugInput, workflow: workflowPayload }
        })
      );
    };
    ws.onmessage = async (event) => {
      const message = JSON.parse(event.data) as WsEvent;
      setLogs((prev) => [...prev, `[${message.type}] ${JSON.stringify(message.payload ?? {})}`]);
      if (message.type === "COMPLETED") {
        const payload = message.payload ?? {};
        const output = payload.output as DebugOutput | undefined;
        setDebugResult(output ?? null);
        if (output?.audioBase64) {
          const audio = new Audio(`data:${output.contentType};base64,${output.audioBase64}`);
          await audio.play();
        }
        setLoading(false);
        ws.close();
      } else if (message.type === "FAILED") {
        setLoading(false);
        ws.close();
      }
    };
    ws.onerror = () => {
      setLogs((prev) => [...prev, "[ERROR] websocket connection failed"]);
      setLoading(false);
    };
    ws.onclose = () => {
      setLoading(false);
    };
  }

  function appendNodeAt(template: { type: NodeType; label: string }, x: number, y: number) {
    const id = `${template.type}-${crypto.randomUUID().slice(0, 8)}`;
    const meta: WorkflowNodeMeta = {
      id,
      type: template.type,
      data: {
        name: template.label
      }
    };
    setNodes((prev) => [
      ...prev,
      {
        id,
        type: "default",
        position: { x, y },
        data: { label: template.label },
        meta
      }
    ]);
  }

  function handleDragStart(event: DragEvent<HTMLButtonElement>, nodeTemplate: { type: NodeType; label: string }) {
    event.dataTransfer.setData("application/walnut-node", JSON.stringify(nodeTemplate));
    event.dataTransfer.effectAllowed = "move";
  }

  function handleDragOver(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    const raw = event.dataTransfer.getData("application/walnut-node");
    if (!raw || !flowRef.current) return;
    const template = JSON.parse(raw) as { type: NodeType; label: string };
    const bounds = event.currentTarget.getBoundingClientRect();
    const position = flowRef.current.project({
      x: event.clientX - bounds.left,
      y: event.clientY - bounds.top
    });
    appendNodeAt(template, position.x, position.y);
  }

  return (
    <div className="page">
      <header className="topbar">
        <div className="brand">
          <strong>PaiAgent</strong>
          <span className="workspace">qoder5</span>
        </div>
        <div className="top-actions">
          <button className="btn ghost">＋ 新建</button>
          <button className="btn ghost">📂 加载</button>
          <button className="btn primary">💾 保存</button>
          <button className="btn primary">🧪 调试</button>
          <span className="user">admin</span>
        </div>
      </header>

      <div className="layout">
        <aside className="panel left">
          <h3>节点库</h3>
          <p className="group-title">📁 大模型节点</p>
          {llmNodes.map((n) => (
            <button key={n.label} className="node-btn" draggable onDragStart={(e) => handleDragStart(e, n)}>
              <span className="node-icon">{n.icon}</span>
              <span>{n.label}</span>
            </button>
          ))}
          <p className="group-title">🔧 工具节点</p>
          {toolNodes.map((n) => (
            <button key={n.label} className="node-btn" draggable onDragStart={(e) => handleDragStart(e, n)}>
              <span className="node-icon">{n.icon}</span>
              <span>{n.label}</span>
            </button>
          ))}
          <p className="left-tip">💡 拖拽节点到画布中使用</p>
        </aside>

        <main className="canvas-wrap" onDragOver={handleDragOver} onDrop={handleDrop}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            fitView
            onInit={(instance) => {
              flowRef.current = instance;
            }}
            onNodeClick={(_, node) => setSelectedNode((node as FlowNode).meta)}
            onPaneClick={() => setSelectedNode(null)}
          >
            <Background gap={22} size={1} />
            <Controls />
          </ReactFlow>
        </main>

        <aside className="panel right">
          <h3>节点配置</h3>
          {selectedNode ? (
            <div className="config-stack">
              <label className="config-label">节点 ID</label>
              <div className="config-input">{selectedNode.id}</div>
              <label className="config-label">节点类型</label>
              <div className="config-input">{selectedNode.type}</div>
              <label className="config-label">输出配置</label>
              <div className="config-row">
                <span className="chip">output</span>
                <span className="chip">引用</span>
                <span className="chip">{selectedNode.data?.name || "字段"}</span>
              </div>
              <label className="config-label">回答内容配置</label>
              <textarea className="config-area" value="{{output}}" readOnly />
              <button className="save-config-btn">保存配置</button>
            </div>
          ) : (
            <p className="hint">点击画布节点查看配置</p>
          )}
        </aside>
      </div>

      <section className={`debug-drawer ${debugOpen ? "open" : ""}`}>
        <div className="debug-header">
          <strong>调试抽屉</strong>
          <button className="toggle-btn" onClick={() => setDebugOpen((v) => !v)}>
            {debugOpen ? "收起" : "展开"}
          </button>
        </div>
        {debugOpen && (
          <div className="debug-body">
            <textarea
              value={debugInput}
              onChange={(e) => setDebugInput(e.target.value)}
              placeholder="输入测试文本..."
            />
            <button className="run-btn" onClick={handleDebug} disabled={loading}>
              {loading ? "调试中..." : "开始调试"}
            </button>
            <div className="result-box">
              <div>执行日志(实时):</div>
              <pre>{logs.length ? logs.join("\n") : "-"}</pre>
              <div>LLM 文本输出:</div>
              <pre>{debugResult?.text || "-"}</pre>
              <div>音频状态: {debugResult?.audioBase64 ? "已返回音频并自动播放" : "未返回音频"}</div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
