import React, { useEffect, useRef, useState } from 'react';

function LogConsole({ logs, running }) {
    const ref = useRef(null);
    useEffect(() => {
        if (ref.current) {
            ref.current.scrollTop = ref.current.scrollHeight;
        }
    }, [logs]);

    if (logs.length === 0 && !running) {
        return null;
    }
    return (
        <div className="log-console">
            <div className="log-head">
                pipeline log {running && <span className="spin">● live</span>}
            </div>
            <pre className="log-body" ref={ref}>
                {logs.join('\n')}
                {running ? '\n▌' : ''}
            </pre>
        </div>
    );
}

export function ResultsPanel({ result, running, logs, backendHint }) {
    const [openPath, setOpenPath] = useState(null);

    const body = () => {
        if (!result) {
            if (running) {
                return null;
            }
            return (
                <div className="placeholder">
                    Click <b>▶ Validate &amp; Generate</b> to run the pipeline on the selected
                    model. The live pipeline log, validation result, and every generated
                    artifact will appear here.
                </div>
            );
        }
        if (result.error) {
            return (
                <div className="error-box">
                    <b>Backend offline</b>
                    <p>{result.error}</p>
                    <p>Start it from the project root, then try again:</p>
                    <pre>{backendHint}</pre>
                </div>
            );
        }

        const artifacts = result.artifacts ?? [];
        const current = artifacts.find((a) => a.path === openPath) ?? artifacts[0];
        return (
            <>
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
            </>
        );
    };

    return (
        <aside className="results">
            <LogConsole logs={logs} running={running} />
            {body()}
        </aside>
    );
}
