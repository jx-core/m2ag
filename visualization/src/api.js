// API client for the M2AG web server, with a graceful fallback to models
// bundled at build time so the visualizer still works as a static app
// (phase 10) when the Java backend is not running.

import ecommerceXmi from '../../models/ecommerce.xmi?raw';
import bankingXmi from '../../models/banking.xmi?raw';

const BUNDLED = {
    'ecommerce.xmi': ecommerceXmi,
    'banking.xmi': bankingXmi,
};

export const BACKEND_HINT =
    'mvn exec:java -Dexec.mainClass=mg.codel.m2ag.web.WebServer';

export async function getModels() {
    try {
        const r = await fetch('/api/models');
        if (r.ok) {
            return await r.json();
        }
    } catch {
        /* backend offline — fall back to bundled */
    }
    return Object.keys(BUNDLED);
}

export async function getModelXmi(name) {
    try {
        const r = await fetch(`/api/model?name=${encodeURIComponent(name)}`);
        if (r.ok) {
            return await r.text();
        }
    } catch {
        /* fall back */
    }
    if (BUNDLED[name]) {
        return BUNDLED[name];
    }
    throw new Error(`Model "${name}" is unavailable (backend offline and not bundled).`);
}

/**
 * Runs the pipeline, streaming live log lines to `onLog(line)` as they arrive,
 * and resolving with the final result object. The server sends newline-tagged
 * lines: 'L' = log line, 'R' = final JSON result.
 */
export async function runPipeline(name, onLog) {
    let r;
    try {
        r = await fetch(`/api/run?name=${encodeURIComponent(name)}`, { method: 'POST' });
    } catch {
        throw new Error('The M2AG backend is not running.');
    }
    if (!r.ok || !r.body) {
        throw new Error(`Backend returned HTTP ${r.status}.`);
    }

    const reader = r.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let result = null;

    for (;;) {
        const { done, value } = await reader.read();
        if (done) {
            break;
        }
        buffer += decoder.decode(value, { stream: true });
        let nl;
        while ((nl = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, nl);
            buffer = buffer.slice(nl + 1);
            if (!line) {
                continue;
            }
            const tag = line[0];
            const rest = line.slice(1);
            if (tag === 'L') {
                onLog?.(rest);
            } else if (tag === 'R') {
                result = JSON.parse(rest);
            }
        }
    }
    if (!result) {
        throw new Error('The server did not return a result.');
    }
    return result;
}
