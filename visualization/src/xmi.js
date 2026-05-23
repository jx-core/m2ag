// Browser-side parser for MicroserviceArchitecture XMI instances.
// Mirrors the structure declared in metamodel/MicroserviceArchitecture.ecore.

const directChildren = (parent, name) =>
    [...parent.children].filter((c) => c.tagName === name);

const attr = (el, name, fallback = null) => {
    const v = el.getAttributeNS('http://www.omg.org/XMI', name.split(':')[1] ?? name);
    if (v !== null) return v;
    return el.getAttribute(name) ?? fallback;
};

export function parseArchitecture(xmiText) {
    const doc = new DOMParser().parseFromString(xmiText, 'application/xml');
    const err = doc.querySelector('parsererror');
    if (err) {
        throw new Error('Invalid XMI: ' + err.textContent);
    }
    const root = doc.documentElement;

    const services = directChildren(root, 'services').map((s) => ({
        id: attr(s, 'xmi:id'),
        name: s.getAttribute('name'),
        port: parseInt(s.getAttribute('port') ?? '0', 10),
        database: s.getAttribute('database'),
        providedInterfaces: directChildren(s, 'providedInterfaces').map((i) => ({
            id: attr(i, 'xmi:id'),
            name: i.getAttribute('name'),
            endpoints: directChildren(i, 'endpoints').map((e) => ({
                method: e.getAttribute('method') ?? 'GET',
                path: e.getAttribute('path'),
                authRequired: e.getAttribute('authRequired') === 'true',
            })),
        })),
        dependencies: directChildren(s, 'dependencies').map((d) => ({
            target: d.getAttribute('target'),
            protocol: d.getAttribute('protocol') ?? 'REST',
        })),
    }));

    const databases = directChildren(root, 'databases').map((d) => ({
        id: attr(d, 'xmi:id'),
        dbType: d.getAttribute('type'),
        host: d.getAttribute('host'),
        port: parseInt(d.getAttribute('port') ?? '0', 10),
    }));

    const brokers = directChildren(root, 'messageBrokers').map((b) => ({
        id: attr(b, 'xmi:id'),
        brokerType: b.getAttribute('type'),
        port: parseInt(b.getAttribute('port') ?? '0', 10),
    }));

    return {
        name: root.getAttribute('name'),
        version: root.getAttribute('version'),
        services,
        databases,
        brokers,
    };
}
