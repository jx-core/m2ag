# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project State

M²AG is an academic Model-Driven Engineering (MDE) project that generates executable
microservice systems from abstract architectural models. **All 11 CDC phases are
implemented and verified.** MVP phases 1–9 (§15) run end-to-end on both reference models
(`ecommerce.xmi`, `banking.xmi`); phase 10 is the React Flow visualizer + unified web app
in [visualization/](visualization/) backed by `mg.codel.m2ag.web.WebServer`; phase 11 is
the VS Code / Theia extension in [theia-extension/](theia-extension/). The rest of CDC §16
(Kubernetes / Kafka / GraphQL / service mesh) is not started.

There are four ways to run the same pipeline: the CLI (`mvn exec:java`), the web app
(`WebServer` + the React UI, no Eclipse), the IDE extension (`.vsix`), and Classic Eclipse
for *authoring* the models (see [docs/ECLIPSE_GUIDE.md](docs/ECLIPSE_GUIDE.md)). The Java
`GenerationPipeline.execute()` is the single shared code path behind the CLI and the web
server.

The thesis framing for all decisions: *"We transform abstract architectural models into
executable distributed systems — not code, models."*

## Authoritative Specification

**[docs/M2AG_CAHIER_DE_CHARGES.md](docs/M2AG_CAHIER_DE_CHARGES.md) is binding.** It is
both the cahier des charges and the detailed Claude Code instruction set. Read it
before changing anything — it contains the exact Ecore class hierarchies, the 8 OCL
rule bodies, ATL rule skeletons, Acceleo output layouts, and per-phase acceptance
criteria. This file only summarizes the cross-cutting rules; the spec wins on any
detail conflict.

[annexes/chatgpt.md](annexes/chatgpt.md) holds the IDE/toolchain rationale only — not
requirements. [README.md](README.md) is the human-facing overview.

## Non-Negotiable Rules

These come from the spec and constrain nearly every task:

1. **Locked tech stack — do not propose alternatives.** EMF/Ecore (M3), XMI
   serialization, OCL validation, ATL for model-to-model, Acceleo for model-to-text.
   Backend generation target is **Spring Boot + Maven only** (no Node.js/Express, no
   multi-backend polymorphism in MVP).

2. **The pipeline order is M2M-then-M2T, never direct.** OCL validation → ATL
   (`architecture2deployment.atl`) produces a `Deployment.ecore` instance → Acceleo
   generates infrastructure artifacts *from the Deployment model*. Acceleo reads the
   `MicroserviceArchitecture.ecore` instance directly only for OpenAPI generation
   (API contracts are a logical, not deployment, concern). Do not invert this.

3. **Two metamodels, deliberately separate.** `MicroserviceArchitecture.ecore` (M2,
   logical) and `Deployment.ecore` (M2, runtime topology). The intermediate deployment
   metamodel is mandatory — it separates logical from runtime semantics.

4. **Interface-based dependencies.** `ServiceDependency.target` points to a
   `ServiceInterface`, never directly to a `Microservice`.

5. **Traceability is a first-class deliverable.** Every generated artifact carries an
   `[M2AG-TRACE]` header naming its source model element, transformation rule, and
   template. `GenerationPipeline` writes `generated/traceability.json` after every
   run. No manual artifact creation outside the pipeline.

6. **Validation halts the pipeline.** All 8 OCL constraints run before any
   transformation. A failure returns `ValidationResult { valid, violations[] }` and
   stops — nothing reaches ATL or Acceleo.

7. **Phase-gated implementation.** Follow the 11-phase order in spec §12. MVP scope is
   spec §15 (phases 1–9, complete); out-of-scope items (Kubernetes, Kafka, Theia
   extension, visualization, etc.) are spec §16 — do not touch them until any
   user-requested MVP changes are verified.

## Architecture Notes

- **Authoring vs. execution**: the `.ecore`, `.ocl`, `.atl`, and `.mtl` files are the
  authoritative model-driven definitions, edited and debugged inside Classic Eclipse
  Modeling Tools (CDC §2). The Java classes in
  [src/main/java/mg/codel/m2ag/pipeline/](src/main/java/mg/codel/m2ag/pipeline/)
  (`DeploymentTransformer`, `DockerComposeGenerator`, `SpringBootGenerator`,
  `OpenApiGenerator`) are deliberate **headless executable mirrors** of those
  artifacts so the full pipeline runs without an Eclipse install. Each Java method
  names the ATL rule or Acceleo template it implements; the two definitions must stay
  in lock-step. If you change an `.atl` rule or `.mtl` template, update its mirror.

- **`ValidationEngine` and `constraints.ocl` are the same relationship.** The OCL file
  is the academic specification (checked in the Eclipse OCL console); the Java engine
  is its pipeline-integrated executor. The DFS transitive-cycle check is CDC §13 Java
  extension (no OCL counterpart).

- **`architecture2openapi.atl` is intentionally absent.** CDC §7 sketches it but never
  defines an OpenAPI metamodel; CDC §8.3 explicitly states `OpenAPI.mtl` reads
  `MicroserviceArchitecture.ecore` directly. The §8.3 reading wins. Do not invent
  an OpenAPI metamodel.

## Build and Run

JDK 17+ and Maven 3.9+. From the repo root:

```bash
mvn compile                                         # build
mvn exec:java                                       # run on models/ecommerce.xmi
mvn exec:java -Dexec.args="models/banking.xmi"      # run on the second reference model
```

Output goes to `models/<name>-deployment.xmi` (the ATL stage's M2M result) and
`generated/{docker,services,openapi,traceability.json}`. Both are gitignored —
they are reproducible from the source models.

The visualizer is a separate Vite + React subproject under `visualization/`:

```bash
cd visualization && npm install && npm run dev    # http://localhost:5173
```

It parses XMI client-side (DOMParser, no derived JSON) so the view stays a thin
projection of the M1 model. `visualization/{node_modules,dist}/` are gitignored.

The unified web app adds a backend that runs the real pipeline:

```bash
cd visualization && npm run build                                  # build UI once
mvn exec:java -Dexec.mainClass=mg.codel.m2ag.web.WebServer         # http://localhost:8080
```

`WebServer` (JDK `com.sun.net.httpserver`, no new deps) serves the built UI and exposes
`/api/{models,model,run}`; `/api/run` calls `GenerationPipeline.execute()`. `execute()`
clears `generated/` each run and always writes `generated/validation-report.json` (the IDE
extension and web UI read it).

The IDE extension lives in `theia-extension/` (TypeScript, VS Code/Theia API):

```bash
cd theia-extension && npm install && npm run compile && npx @vscode/vsce package
```

`theia-extension/{node_modules,out}/` and `*.vsix` are gitignored.

## Toolchain Workflow

Build and validate the entire MDE core (Ecore metamodels, ATL, Acceleo, OCL) inside
**Classic Eclipse (Eclipse Modeling Tools package)** first — that ecosystem is the
mature one. Theia + Claude Code is the integration shell for the Java pipeline
orchestration, not the foundation.

**Scope boundary for Claude:** resolve modeling logic, structure, transformation rules,
code generation, and the headless Java pipeline. Do not attempt to fix EMF classpath
issues, ATL runtime OSGi conflicts, or Eclipse plugin incompatibilities in Classic
Eclipse — those are resolved manually there.

## Environment Notes

- A `PreToolUse` hook (`.claude/hooks/file-backup-hook.js`) snapshots any file before
  the first Write/Edit of a session into `.claude/.edit-baks/<session_id>/`; a `Stop`
  hook clears it. This is local safety only — no action needed.
- `annexes/` is gitignored (contains `github-credentials-guide.txt` and other local
  notes); `docs/` is tracked.
