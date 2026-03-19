const SUPPORTED_NODE_TYPES = new Set(["input", "llm", "tool_tts", "output"]);

function topologicalSort(nodes, edges) {
  const nodeMap = new Map(nodes.map((node) => [node.id, node]));
  const inDegree = new Map(nodes.map((node) => [node.id, 0]));
  const graph = new Map(nodes.map((node) => [node.id, []]));

  for (const edge of edges) {
    if (!nodeMap.has(edge.source) || !nodeMap.has(edge.target)) {
      throw new Error(`Invalid edge: ${edge.source} -> ${edge.target}`);
    }
    graph.get(edge.source).push(edge.target);
    inDegree.set(edge.target, inDegree.get(edge.target) + 1);
  }

  const queue = [];
  for (const [id, degree] of inDegree.entries()) {
    if (degree === 0) queue.push(id);
  }

  const result = [];
  while (queue.length > 0) {
    const id = queue.shift();
    result.push(nodeMap.get(id));
    for (const neighbor of graph.get(id)) {
      inDegree.set(neighbor, inDegree.get(neighbor) - 1);
      if (inDegree.get(neighbor) === 0) queue.push(neighbor);
    }
  }

  if (result.length !== nodes.length) {
    throw new Error("Workflow contains cycle(s).");
  }

  return result;
}

export async function executeWorkflow(definition, services, inputText) {
  const { nodes, edges } = definition;
  const sortedNodes = topologicalSort(nodes, edges);
  const values = {};
  let outputValue = null;

  for (const node of sortedNodes) {
    if (!SUPPORTED_NODE_TYPES.has(node.type)) {
      throw new Error(`Unsupported node type: ${node.type}`);
    }

    if (node.type === "input") {
      values[node.id] = { text: inputText };
      continue;
    }

    if (node.type === "llm") {
      const previous = resolveSingleIncomingValue(node.id, edges, values);
      const prompt = previous?.text || inputText;
      const model = node.data?.model || "deepseek-chat";
      const text = await services.callLLM({ prompt, model });
      values[node.id] = { text };
      continue;
    }

    if (node.type === "tool_tts") {
      const previous = resolveSingleIncomingValue(node.id, edges, values);
      const text = previous?.text || "";
      const voice = node.data?.voice || "zh-CN-XiaoxiaoNeural";
      const result = await services.synthesizeAudio({ text, voice });
      values[node.id] = { text, ...result };
      continue;
    }

    if (node.type === "output") {
      outputValue = resolveSingleIncomingValue(node.id, edges, values);
      values[node.id] = outputValue;
    }
  }

  return {
    values,
    output: outputValue || {}
  };
}

function resolveSingleIncomingValue(nodeId, edges, values) {
  const incoming = edges.find((edge) => edge.target === nodeId);
  if (!incoming) return null;
  return values[incoming.source] || null;
}
