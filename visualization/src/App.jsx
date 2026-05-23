import React, { useMemo, useState } from 'react';
import { ReactFlow, Background, Controls, MiniMap } from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import ecommerceXmi from '../../models/ecommerce.xmi?raw';
import bankingXmi from '../../models/banking.xmi?raw';

import { parseArchitecture } from './xmi.js';
import { buildGraph } from './graph.js';
import { nodeTypes } from './nodes.jsx';

const MODELS = {
    'ecommerce.xmi': ecommerceXmi,
    'banking.xmi': bankingXmi,
};

export default function App() {
    const [selected, setSelected] = useState('ecommerce.xmi');
    const model = useMemo(() => parseArchitecture(MODELS[selected]), [selected]);
    const { nodes, edges } = useMemo(() => buildGraph(model), [model]);

    return (
        <div className="layout">
            <header className="topbar">
                <strong>M²AG visualizer</strong>
                <span className="sep">·</span>
                <span>
                    {model.name} v{model.version}
                </span>
                <span className="sep">·</span>
                <label>
                    model:&nbsp;
                    <select value={selected} onChange={(e) => setSelected(e.target.value)}>
                        {Object.keys(MODELS).map((k) => (
                            <option key={k}>{k}</option>
                        ))}
                    </select>
                </label>
                <span className="sep">·</span>
                <span className="muted">
                    {model.services.length} services · {model.databases.length} dbs ·{' '}
                    {model.brokers.length} brokers
                </span>
            </header>
            <div className="canvas">
                <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} fitView>
                    <Background gap={24} />
                    <MiniMap pannable zoomable />
                    <Controls />
                </ReactFlow>
            </div>
        </div>
    );
}
