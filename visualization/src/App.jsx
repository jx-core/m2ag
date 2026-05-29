import React, { useEffect, useMemo, useState } from 'react';
import { ReactFlow, Background, Controls, MiniMap } from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { getModels, getModelXmi, runPipeline, BACKEND_HINT } from './api.js';
import { parseArchitecture } from './xmi.js';
import { buildGraph } from './graph.js';
import { nodeTypes } from './nodes.jsx';
import { ResultsPanel } from './results.jsx';

export default function App() {
    const [models, setModels] = useState([]);
    const [selected, setSelected] = useState('');
    const [xmi, setXmi] = useState('');
    const [running, setRunning] = useState(false);
    const [result, setResult] = useState(null);

    useEffect(() => {
        getModels().then((ms) => {
            setModels(ms);
            setSelected((cur) => cur || ms[0] || '');
        });
    }, []);

    useEffect(() => {
        if (!selected) {
            return;
        }
        setResult(null);
        getModelXmi(selected)
            .then(setXmi)
            .catch(() => setXmi(''));
    }, [selected]);

    const model = useMemo(() => {
        try {
            return xmi ? parseArchitecture(xmi) : null;
        } catch {
            return null;
        }
    }, [xmi]);

    const { nodes, edges } = useMemo(
        () => (model ? buildGraph(model) : { nodes: [], edges: [] }),
        [model],
    );

    async function onRun() {
        if (!selected) {
            return;
        }
        setRunning(true);
        setResult(null);
        try {
            setResult(await runPipeline(selected));
        } catch (e) {
            setResult({ error: e.message });
        } finally {
            setRunning(false);
        }
    }

    return (
        <div className="layout">
            <header className="topbar">
                <strong>M²AG</strong>
                <span className="sep">·</span>
                <label>
                    model:&nbsp;
                    <select value={selected} onChange={(e) => setSelected(e.target.value)}>
                        {models.map((m) => (
                            <option key={m}>{m}</option>
                        ))}
                    </select>
                </label>
                <button className="run-btn" onClick={onRun} disabled={running || !selected}>
                    {running ? 'Generating…' : '▶ Validate & Generate'}
                </button>
                {model && (
                    <span className="muted">
                        {model.name} v{model.version} · {model.services.length} services ·{' '}
                        {model.databases.length} dbs · {model.brokers.length} brokers
                    </span>
                )}
            </header>
            <div className="main">
                <div className="canvas">
                    <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} fitView>
                        <Background gap={24} />
                        <MiniMap pannable zoomable />
                        <Controls />
                    </ReactFlow>
                </div>
                <ResultsPanel result={result} running={running} backendHint={BACKEND_HINT} />
            </div>
        </div>
    );
}
