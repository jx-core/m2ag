import React from 'react';
import { Handle, Position } from '@xyflow/react';

export function ServiceNode({ data }) {
    return (
        <div className="node service">
            <Handle type="target" position={Position.Top} />
            <div className="title">{data.name}</div>
            <div className="meta">port {data.port}</div>
            {data.providedInterfaces.flatMap((i) =>
                i.endpoints.map((e, k) => (
                    <div className="endpoint" key={`${i.id}-${k}`}>
                        <span className={`method m-${e.method}`}>{e.method}</span>
                        <span className="path">{e.path}</span>
                        {e.authRequired && <span className="auth">🔒</span>}
                    </div>
                )),
            )}
            <Handle type="source" position={Position.Bottom} />
        </div>
    );
}

export function DatabaseNode({ data }) {
    return (
        <div className="node database">
            <Handle type="target" position={Position.Top} />
            <div className="title">{data.dbType}</div>
            <div className="meta">port {data.port}</div>
        </div>
    );
}

export function BrokerNode({ data }) {
    return (
        <div className="node broker">
            <Handle type="target" position={Position.Top} />
            <div className="title">{data.brokerType}</div>
            <div className="meta">port {data.port}</div>
        </div>
    );
}

export const nodeTypes = {
    service: ServiceNode,
    database: DatabaseNode,
    broker: BrokerNode,
};
