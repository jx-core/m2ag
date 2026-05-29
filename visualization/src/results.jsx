import React, { useState } from 'react';

export function ResultsPanel({ result, running, backendHint }) {
    const [openPath, setOpenPath] = useState(null);

    if (running) {
        return (
            <aside className="results">
                <div className="placeholder">Running the pipeline…</div>
            </aside>
        );
    }
    if (!result) {
        return (
            <aside className="results">
                <div className="placeholder">
                    Click <b>▶ Validate &amp; Generate</b> to run the pipeline on the selected
                    model. Validation, the deployment model, and every generated artifact will
                    appear here.
                </div>
            </aside>
        );
    }
    if (result.error) {
        return (
            <aside className="results">
                <div className="error-box">
                    <b>Backend offline</b>
                    <p>{result.error}</p>
                    <p>Start it from the project root, then try again:</p>
                    <pre>{backendHint}</pre>
                </div>
            </aside>
        );
    }

    const artifacts = result.artifacts ?? [];
    const current = artifacts.find((a) => a.path === openPath) ?? artifacts[0];

    return (
        <aside className="results">
            <div className={`badge ${result.valid ? 'ok' : 'bad'}`}>
                {result.valid
                    ? '✓ VALID — model passed all 8 OCL rules'
                    : `✗ INVALID — ${result.violations.length} violation(s); nothing generated`}
            </div>

            {!result.valid && (
                <ul className="violations">
                    {result.violations.map((v, i) => (
                        <li key={i}>
                            <div>
                                <span className="rule">{v.rule}</span>
                                <span className="eid">{v.elementId}</span>
                            </div>
                            <div className="vmsg">{v.message}</div>
                        </li>
                    ))}
                </ul>
            )}

            {result.valid && (
                <div className="files">
                    <div className="file-list">
                        <div className="file-list-head">{artifacts.length} artifacts</div>
                        {artifacts.map((a) => (
                            <div
                                key={a.path}
                                className={`file ${current && current.path === a.path ? 'active' : ''}`}
                                onClick={() => setOpenPath(a.path)}
                                title={a.path}
                            >
                                {a.path}
                            </div>
                        ))}
                    </div>
                    <pre className="file-content">{current ? current.content : ''}</pre>
                </div>
            )}
        </aside>
    );
}
