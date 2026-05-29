import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

interface TraceEntry {
    file: string;
    sourceElement: string;
    transformation: string;
    template: string;
}

class TraceNode extends vscode.TreeItem {
    children?: TraceNode[];

    constructor(label: string, state: vscode.TreeItemCollapsibleState) {
        super(label, state);
    }
}

/**
 * Tree view fed by generated/traceability.json. Top level groups artifacts by
 * their source model element; leaves open the generated file.
 */
export class TraceabilityProvider implements vscode.TreeDataProvider<TraceNode> {
    private readonly emitter = new vscode.EventEmitter<void>();
    readonly onDidChangeTreeData = this.emitter.event;

    constructor(private readonly root: string | undefined) {}

    refresh(): void {
        this.emitter.fire();
    }

    getTreeItem(node: TraceNode): vscode.TreeItem {
        return node;
    }

    getChildren(node?: TraceNode): TraceNode[] {
        if (!this.root) {
            return [];
        }
        const traceFile = path.join(this.root, 'generated', 'traceability.json');

        if (!fs.existsSync(traceFile)) {
            if (node) {
                return [];
            }
            const hint = new TraceNode(
                'Run "M2AG: Generate from Model"',
                vscode.TreeItemCollapsibleState.None,
            );
            hint.command = { command: 'm2ag.runPipeline', title: 'Run' };
            hint.iconPath = new vscode.ThemeIcon('play');
            return [hint];
        }

        let data: { model?: string; artifacts?: TraceEntry[] };
        try {
            data = JSON.parse(fs.readFileSync(traceFile, 'utf8'));
        } catch {
            return [];
        }
        const entries = data.artifacts ?? [];

        if (node) {
            return node.children ?? [];
        }

        const sources = [...new Set(entries.map((e) => e.sourceElement))];
        return sources.map((source) => {
            const group = new TraceNode(source, vscode.TreeItemCollapsibleState.Expanded);
            group.iconPath = new vscode.ThemeIcon('symbol-class');
            group.children = entries
                .filter((e) => e.sourceElement === source)
                .map((e) => {
                    const leaf = new TraceNode(
                        path.basename(e.file),
                        vscode.TreeItemCollapsibleState.None,
                    );
                    leaf.description = e.template;
                    leaf.tooltip =
                        `${e.file}\nsource: ${e.sourceElement}\n` +
                        `transformation: ${e.transformation}\ntemplate: ${e.template}`;
                    const abs = vscode.Uri.file(path.join(this.root!, 'generated', e.file));
                    leaf.resourceUri = abs;
                    leaf.command = { command: 'vscode.open', title: 'Open', arguments: [abs] };
                    return leaf;
                });
            return group;
        });
    }
}
