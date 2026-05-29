# M²AG — Model-Driven Microservice Architecture Generator

> *"We transform abstract architectural models into executable distributed systems — not code, models."*

Academic MDE project (IDM / MDE — ENI, Université de Fianarantsoa).
Transforms a logical microservice architecture model into a runnable Docker
Compose + Spring Boot + OpenAPI artifact set through the full
EMF / OCL / ATL / Acceleo pipeline.

The authoritative specification is
[docs/M2AG_CAHIER_DE_CHARGES.md](docs/M2AG_CAHIER_DE_CHARGES.md) — it is both
the cahier des charges and the detailed instruction set the implementation
follows.

## Four ways to use it

The same pipeline is reachable four ways — pick whichever suits the moment.
**Only the last needs Eclipse**, and only for authoring/proving the models.

| Way | What it's for | Command |
|---|---|---|
| **Web app** | Pick a model, click *Validate & Generate*, browse results — fully in the browser, no Eclipse | build the UI once (`cd visualization && npm run build`), then `mvn exec:java -Dexec.mainClass=mg.codel.m2ag.web.WebServer` → <http://localhost:8080> |
| **Command line** | Headless run / CI / demos | `mvn exec:java` (defaults to `models/ecommerce.xmi`) |
| **IDE extension** | Run from VS Code / Theia, see violations + traceability in the editor | install the `.vsix` from [theia-extension/](theia-extension/) |
| **Classic Eclipse** | *Author and academically prove* the `.ecore`/`.ocl`/`.atl`/`.mtl` | see [docs/ECLIPSE_GUIDE.md](docs/ECLIPSE_GUIDE.md) |

## Pipeline

```
ecommerce.xmi                              (M1 — logical architecture)
    │   load + register dynamic Ecore metamodels (EMF)
    ▼
constraints.ocl ──► ValidationEngine       (8 OCL rules + DFS cycle check)
    │   halts on any violation
    ▼
architecture2deployment.atl ──► DeploymentTransformer
    ▼
ecommerce-deployment.xmi                   (M1 — runtime topology)
    │
    ├──► DockerCompose.mtl     ──► generated/docker/docker-compose.yml
    ├──► SpringBootService.mtl ──► generated/services/{Name}/...
    └──► OpenAPI.mtl           ──► generated/openapi/{Name}-openapi.yaml
                                   + generated/traceability.json
```

## Repository layout

| Path | Layer | Content |
|---|---|---|
| [metamodel/MicroserviceArchitecture.ecore](metamodel/MicroserviceArchitecture.ecore) | M2 | Logical architecture metamodel (CDC §4) |
| [metamodel/Deployment.ecore](metamodel/Deployment.ecore) | M2 | Runtime topology metamodel (CDC §5) |
| [metamodel/constraints.ocl](metamodel/constraints.ocl) | OCL | 8 invariants — halt the pipeline on failure (CDC §6) |
| [models/ecommerce.xmi](models/ecommerce.xmi), [models/banking.xmi](models/banking.xmi) | M1 | Reference architecture instances (CDC §10) |
| [transformations/architecture2deployment.atl](transformations/architecture2deployment.atl) | M2M | Architecture → deployment topology (CDC §7) |
| [generators/acceleo/](generators/acceleo/) | M2T | DockerCompose, SpringBootService, OpenAPI templates (CDC §8) |
| [src/main/java/mg/codel/m2ag/](src/main/java/mg/codel/m2ag/) | Java | Pipeline orchestrator, validation, EMF-based mirrors of ATL/Acceleo, web server |
| [visualization/](visualization/) | UI | React Flow visualizer + unified web app (CDC phase 10) |
| [theia-extension/](theia-extension/) | IDE | VS Code / Theia extension (CDC phase 11) |
| `generated/` | M0 | Output (gitignored — reproducible via `mvn exec:java`) |
| [docs/](docs/) | — | CDC and reference documentation |

## Running the pipeline

Requires JDK 17+ and Maven 3.9+.

```bash
mvn compile                                              # build
mvn exec:java                                            # run on models/ecommerce.xmi
mvn exec:java -Dexec.args="models/banking.xmi"           # second reference model
mvn exec:java -Dexec.args="path/to/your-model.xmi"       # any conforming M1 instance
```

Successful run on `ecommerce.xmi`:

```
=== M2AG generation pipeline ===
input model : models/ecommerce.xmi
[1] loaded    : SystemArchitecture(ECommerceSystem)
[2] validated : PASS (8 OCL rules + transitive cycle check)
[3] M2M (ATL) : models/ecommerce-deployment.xmi
[4] M2T (MTL) : 22 artifacts in generated
=== pipeline complete (22 artifacts) ===
```

When validation fails, the pipeline prints every violation with rule name and
element id, exits non-zero, and writes only `generated/validation-report.json`.

### Web app (no Eclipse, no command line after launch)

```bash
cd visualization && npm install && npm run build     # build the UI once
cd .. && mvn exec:java -Dexec.mainClass=mg.codel.m2ag.web.WebServer
```

Open <http://localhost:8080>: pick a model, click **Validate & Generate**, and
browse the validation result, the dependency graph, and every generated file —
all served by the real pipeline. For UI development, run `npm run dev` in
`visualization/` (Vite proxies `/api` to the server).

## Authoring versus execution

The CDC requires the MDE core to be authored and validated in **Classic
Eclipse (Eclipse Modeling Tools)** — the mature toolchain for EMF, ATL, OCL,
and Acceleo. The Java pipeline is the headless integration point.

| Concern | Where |
|---|---|
| Edit `.ecore`, debug ATL rules, run OCL console, debug Acceleo templates | Classic Eclipse Modeling Tools — see [docs/ECLIPSE_GUIDE.md](docs/ECLIPSE_GUIDE.md) |
| Run the full pipeline headless (CI, demos) | `mvn exec:java` |
| Run + inspect from a browser | the web app (`WebServer`) |
| Run + inspect from the editor | the VS Code / Theia extension |

The Java classes `DeploymentTransformer`, `DockerComposeGenerator`,
`SpringBootGenerator`, `OpenApiGenerator` are deliberate **executable mirrors**
of `transformations/architecture2deployment.atl` and the `generators/acceleo/*.mtl`
templates: every method names the ATL rule or Acceleo template it implements,
so the two definitions stay in lock-step. The `.atl` and `.mtl` files remain
the authoritative model-driven definitions.

## Traceability

Every generated file carries an `[M2AG-TRACE]` header naming its source model
element and the template that produced it. `generated/traceability.json`
records the full artifact-to-model map after each run (CDC §9.3).

## CDC clarifications

- **`architecture2openapi.atl` is intentionally not implemented.** CDC §7
  sketches it but never defines an OpenAPI metamodel; CDC §8.3 explicitly
  states that `OpenAPI.mtl` reads `MicroserviceArchitecture.ecore` directly.
  The §8.3 reading wins — there is no intermediate OpenAPI model.
- `Microservice` carries `environmentVariables` and `deploymentConfig`
  containment references. The §5.2 Architecture→Deployment mapping (which
  uses `EnvironmentVariable`) requires this, even though the §4.1 hierarchy
  sketch omits them.

## Visualization (CDC phase 10)

A small React Flow app in [visualization/](visualization/) renders the
service / dependency graph directly from an XMI model in the browser. It
parses the architecture XMI client-side (no derived JSON) so the visualizer
stays a thin view over the M1 model.

```bash
cd visualization
npm install
npm run dev        # http://localhost:5173
npm run build      # production bundle in dist/
```

The model picker switches between `ecommerce.xmi` and `banking.xmi`; nodes
show services (with their endpoints), databases, and message brokers; edges
distinguish REST, gRPC, and EVENT protocols and dashed database links.

## Phase status

All 11 CDC phases implemented and verified: MVP phases 1–9 (§15), phase 10
(visualization + unified web app), and phase 11 (VS Code / Theia extension).
The rest of CDC §16 (Kubernetes, Kafka, GraphQL, service mesh) remains out of
scope.
