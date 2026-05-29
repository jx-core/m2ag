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

export async function runPipeline(name) {
    let r;
    try {
        r = await fetch(`/api/run?name=${encodeURIComponent(name)}`, { method: 'POST' });
    } catch {
        throw new Error('The M2AG backend is not running.');
    }
    if (!r.ok) {
        throw new Error(`Backend returned HTTP ${r.status}.`);
    }
    return r.json();
}
