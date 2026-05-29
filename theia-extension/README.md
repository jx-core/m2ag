# M²AG — IDE extension (CDC phase 11)

IDE shell integration for the M²AG model-driven pipeline. Runs in **VS Code**
and **Eclipse Theia** (same extension API). It is a convenience layer over the
Java pipeline — not part of the generator itself.

## What it adds

- **Command `M2AG: Generate from Model`** — right-click an `.xmi` model (or use
  the editor title button) to run the full pipeline (`mvn exec:java`). Output
  streams to the *M2AG* output channel.
- **OCL violations as diagnostics** — if validation fails, each violation is
  shown in the Problems panel, mapped (best-effort) to the offending
  `xmi:id` in the model file. Nothing is generated until the model is valid.
- **M²AG Traceability view** — a tree in the Explorer sidebar, built from
  `generated/traceability.json`, grouping every generated artifact under its
  source model element. Click a leaf to open the file.
- **Command `M2AG: Open Architecture Graph`** — starts the React visualizer.

## Requirements

- The workspace must be the M²AG project root (so `mvn` and `generated/` resolve).
- JDK 17+ and Maven 3.9+ on `PATH` (the extension shells out to `mvn`).

## Build & install (two clicks)

```bash
cd theia-extension
npm install
npm run compile
npx @vscode/vsce package        # produces m2ag-extension-1.0.0.vsix
```

Then in VS Code or Theia: **Extensions panel → ⋯ menu → Install from VSIX…**
and pick the generated `.vsix`. No marketplace, no Eclipse plugin machinery.

### Develop without packaging

Open the `theia-extension/` folder in VS Code and press **F5** ("Run Extension")
to launch an Extension Development Host with the extension loaded.
