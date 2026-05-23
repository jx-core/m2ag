// Converts a parsed architecture model into React Flow nodes and edges.

const COLUMN = 280;
const ROW = 220;

export function buildGraph(model) {
    const interfaceOwner = {};
    for (const s of model.services) {
        for (const i of s.providedInterfaces) {
            interfaceOwner[i.id] = s.id;
        }
    }

    const nodes = [];
    const edges = [];

    model.services.forEach((s, i) => {
        nodes.push({
            id: s.id,
            type: 'service',
            position: { x: i * COLUMN, y: 0 },
            data: { ...s },
        });
    });

    model.databases.forEach((d, i) => {
        nodes.push({
            id: d.id,
            type: 'database',
            position: { x: i * COLUMN, y: ROW },
            data: { ...d },
        });
    });

    model.brokers.forEach((b, i) => {
        nodes.push({
            id: b.id,
            type: 'broker',
            position: { x: i * COLUMN, y: ROW * 2 },
            data: { ...b },
        });
    });

    for (const s of model.services) {
        if (s.database) {
            edges.push({
                id: `${s.id}--db--${s.database}`,
                source: s.id,
                target: s.database,
                label: 'db',
                style: { stroke: '#9ca3af', strokeDasharray: '4 4' },
            });
        }
        for (const d of s.dependencies) {
            const targetService = interfaceOwner[d.target];
            if (!targetService) continue;
            edges.push({
                id: `${s.id}--${d.target}`,
                source: s.id,
                target: targetService,
                label: d.protocol,
                animated: d.protocol === 'EVENT',
                style: { stroke: d.protocol === 'EVENT' ? '#f59e0b' : '#2563eb' },
            });
        }
    }

    return { nodes, edges };
}
