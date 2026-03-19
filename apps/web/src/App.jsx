import { useEffect, useMemo, useState } from "react";
import ReactFlow, { Background, Controls } from "reactflow";
import "reactflow/dist/style.css";

const API_BASE = "http://localhost:8787";

const leftPanelNodes = [
  { type: "llm", label: "通义千问" },
  { type: "llm", label: "DeepSeek" },
  { type: "tool_tts", label: "超拟人音频合成" }
];

const nodeTypeLabel = {
  input: "输入",
  llm: "大模型",
  tool_tts: "工具",
  output: "输出"
};

export default function App() {
  const [token, setToken] = useState("");
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [selectedNode, setSelectedNode] = useState(null);
  const [debugOpen, setDebugOpen] = useState(true);
  const [debugInput, setDebugInput] = useState("你好，帮我生成一期关于 AI Agent 的播客开场白。");
  const [debugResult, setDebugResult] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loginAndLoad();
  }, []);

  async function loginAndLoad() {
    const loginResp = await fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "admin", password: "admin123" })
    });
    const loginData = await loginResp.json();
    const nextToken = loginData.token;
    setToken(nextToken);

    const workflowResp = await fetch(`${API_BASE}/api/workflows/default`, {
      headers: { Authorization: `Bearer ${nextToken}` }
    });
    const workflow = await workflowResp.json();
        const flowNodes = workflow.nodes.map((node, index) => ({
          id: node.id,
          type: "default",
          position: { x: 300, y: 60 + index * 140 },
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

  async function handleDebug() {
    setLoading(true);
    try {
      const resp = await fetch(`${API_BASE}/api/workflows/debug`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          input: debugInput,
          workflow: workflowPayload
        })
      });

      const data = await resp.json();
      setDebugResult(data.output || null);
      if (data.output?.audioBase64) {
        const audio = new Audio(`data:${data.output.contentType};base64,${data.output.audioBase64}`);
        await audio.play();
      }
    } finally {
      setLoading(false);
    }
  }

  function appendNode(template) {
    const id = `${template.type}-${crypto.randomUUID().slice(0, 8)}`;
    const meta = {
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
        position: { x: 100, y: 100 + prev.length * 70 },
        data: { label: template.label },
        meta
      }
    ]);
  }

  return (
    <div className="layout">
      <aside className="panel left">
        <h3>节点库</h3>
        <p className="group-title">大模型节点</p>
        {leftPanelNodes
          .filter((n) => n.type === "llm")
          .map((n) => (
            <button key={n.label} className="node-btn" onClick={() => appendNode(n)}>
              {n.label}
            </button>
          ))}
        <p className="group-title">工具节点</p>
        {leftPanelNodes
          .filter((n) => n.type === "tool_tts")
          .map((n) => (
            <button key={n.label} className="node-btn" onClick={() => appendNode(n)}>
              {n.label}
            </button>
          ))}
      </aside>

      <main className="canvas-wrap">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          fitView
          onNodeClick={(_, node) => setSelectedNode(node.meta)}
          onPaneClick={() => setSelectedNode(null)}
        >
          <Background />
          <Controls />
        </ReactFlow>
      </main>

      <aside className="panel right">
        <h3>节点配置</h3>
        {selectedNode ? (
          <div className="config-card">
            <div>节点 ID: {selectedNode.id}</div>
            <div>节点类型: {selectedNode.type}</div>
            <div>节点名称: {selectedNode.data?.name || "-"}</div>
          </div>
        ) : (
          <p className="hint">点击画布节点查看配置</p>
        )}
      </aside>

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
              <div>LLM 文本输出:</div>
              <pre>{debugResult?.text || "-"}</pre>
              <div>音频状态: {debugResult?.audioBase64 ? "已返回音频并自动播放" : "使用浏览器语音兜底播放"}</div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
