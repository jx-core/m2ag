import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import { TraceabilityProvider } from './traceabilityView';

interface Violation {
    rule: string;
    elementId: string;
    message: string;
}
interface ValidationReport {
    valid: boolean;
    violations: Violation[];
}

let channel: vscode.OutputChannel;
let diagnostics: vscode.DiagnosticCollection;

export function activate(context: vscode.ExtensionContext): void {
    channel = vscode.window.createOutputChannel('M2AG');
    diagnostics = vscode.languages.createDiagnosticCollection('m2ag');
    const tree = new TraceabilityProvider(workspaceRoot());

    context.subscriptions.push(
        channel,
        diagnostics,
        vscode.window.registerTreeDataProvider('m2agTraceability', tree),
        vscode.commands.registerCommand('m2ag.runPipeline', (uri?: vscode.Uri) =>
            runPipeline(uri, tree),
        ),
        vscode.commands.registerCommand('m2ag.refreshTraceability', () => tree.refresh()),
        vscode.commands.registerCommand('m2ag.openGraph', openGraph),
    );
}

export function deactivate(): void {
    /* nothing to dispose beyond context.subscriptions */
}

function workspaceRoot(): string | undefined {
    return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
}

async function runPipeline(uri: vscode.Uri | undefined, tree: TraceabilityProvider): Promise<void> {
    const root = workspaceRoot();
    if (!root) {
        vscode.window.showErrorMessage('M2AG: open the project folder first.');
        return;
    }
    const model = await pickModel(root, uri);
    if (!model) {
        return;
    }
    const rel = path.relative(root, model.fsPath);

    channel.clear();
    channel.show(true);
    channel.appendLine(`▶ mvn exec:java -Dexec.args="${rel}"`);

    const code = await vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: `M2AG: generating from ${path.basename(rel)}…`,
        },
        () => execMaven(root, rel),
    );

    const report = readJson<ValidationReport>(
        path.join(root, 'generated', 'validation-report.json'),
    );
    await publishDiagnostics(model, report);
    tree.refresh();

    if (report && report.valid === false) {
        vscode.window.showErrorMessage(
            `M2AG: validation failed — ${report.violations.length} violation(s). See the Problems panel.`,
        );
    } else if (report && report.valid === true) {
        vscode.window.showInformationMessage('M2AG: model valid — artifacts generated.');
    } else if (code !== 0) {
        vscode.window.showWarningMessage(
            'M2AG: pipeline did not complete. See the M2AG output channel.',
        );
    }
}

function execMaven(root: string, rel: string): Promise<number> {
    return new Promise((resolve) => {
        const proc = cp.spawn('mvn', ['-q', '-B', 'exec:java', `-Dexec.args=${rel}`], {
            cwd: root,
            shell: process.platform === 'win32',
        });
        proc.stdout.on('data', (d: Buffer) => channel.append(d.toString()));
        proc.stderr.on('data', (d: Buffer) => channel.append(d.toString()));
        proc.on('error', (e: Error) => {
            channel.appendLine(`✖ failed to start mvn: ${e.message}\n(Is Maven on your PATH?)`);
            resolve(-1);
        });
        proc.on('close', (code: number | null) => {
            channel.appendLine(`\n— mvn exited with code ${code} —`);
            resolve(code ?? -1);
        });
    });
}

async function pickModel(root: string, uri?: vscode.Uri): Promise<vscode.Uri | undefined> {
    const isInput = (p: string) => p.endsWith('.xmi') && !p.endsWith('-deployment.xmi');

    if (uri && isInput(uri.fsPath)) {
        return uri;
    }
    const active = vscode.window.activeTextEditor?.document.uri;
    if (active && isInput(active.fsPath)) {
        return active;
    }
    const found = (await vscode.workspace.findFiles('models/*.xmi')).filter((f) =>
        isInput(f.fsPath),
    );
    if (found.length === 0) {
        vscode.window.showErrorMessage('M2AG: no input .xmi models found under models/.');
        return undefined;
    }
    if (found.length === 1) {
        return found[0];
    }
    const pick = await vscode.window.showQuickPick(
        found.map((f) => ({ label: path.basename(f.fsPath), uri: f })),
        { placeHolder: 'Select a model to generate from' },
    );
    return pick?.uri;
}

async function publishDiagnostics(
    model: vscode.Uri,
    report: ValidationReport | undefined,
): Promise<void> {
    diagnostics.delete(model);
    if (!report || report.valid !== false) {
        return;
    }
    const doc = await vscode.workspace.openTextDocument(model);
    const text = doc.getText();
    const diags = report.violations.map((v) => {
        let range = new vscode.Range(0, 0, 0, 1);
        const needle = `xmi:id="${v.elementId}"`;
        const idx = text.indexOf(needle);
        if (idx >= 0) {
            range = new vscode.Range(doc.positionAt(idx), doc.positionAt(idx + needle.length));
        }
        const d = new vscode.Diagnostic(
            range,
            `[${v.rule}] ${v.message}`,
            vscode.DiagnosticSeverity.Error,
        );
        d.source = 'M2AG / OCL';
        return d;
    });
    diagnostics.set(model, diags);
}

async function openGraph(): Promise<void> {
    const root = workspaceRoot();
    if (!root) {
        return;
    }
    const viz = path.join(root, 'visualization');
    if (!fs.existsSync(viz)) {
        vscode.window.showErrorMessage('M2AG: visualization/ folder not found.');
        return;
    }
    const term = vscode.window.createTerminal({ name: 'M2AG visualizer', cwd: viz });
    term.show();
    term.sendText('npm install && npm run dev');
    vscode.window.showInformationMessage(
        'M2AG: starting the visualizer — open the http://localhost:5173 link printed in the terminal.',
    );
}

function readJson<T>(file: string): T | undefined {
    try {
        return JSON.parse(fs.readFileSync(file, 'utf8')) as T;
    } catch {
        return undefined;
    }
}
