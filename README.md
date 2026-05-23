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
| [src/main/java/mg/codel/m2ag/](src/main/java/mg/codel/m2ag/) | Java | Pipeline orchestrator, validation, EMF-based mirrors of ATL/Acceleo |
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
[5] traceable : generated/traceability.json
=== pipeline complete ===
```

When validation fails, the pipeline prints every violation with rule name and
element id, exits non-zero, and writes nothing.

## Authoring versus execution

The CDC requires the MDE core to be authored and validated in **Classic
Eclipse (Eclipse Modeling Tools)** — the mature toolchain for EMF, ATL, OCL,
and Acceleo. The Java pipeline is the headless integration point.

| Concern | Where |
|---|---|
| Edit `.ecore`, debug ATL rules, run OCL console, debug Acceleo templates | Classic Eclipse Modeling Tools |
| Run the full pipeline headless (CI, demos) | `mvn exec:java` |
| AI-assisted Java orchestration | Theia + Claude Code |

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

## Phase status

All MVP phases 1–9 from CDC §15 are implemented and verified end-to-end on
both `ecommerce.xmi` and `banking.xmi`. Out-of-scope per CDC §16: React Flow
visualization (phase 10), Theia extension (phase 11), Kubernetes manifests,
Kafka modelling.
