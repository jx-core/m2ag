# M²AG — Model-Driven Microservice Architecture Generator
## Cahier des Charges + Claude Code Instructions

> **Status**: Active Development  
> **Context**: Academic MDE project (IDM/MDE course, ENI — Université de Fianarantsoa)  
> **Scope**: MVP — do not overengineer. The academic value is in metamodel quality, transformation correctness, and pipeline automation.

---

## 1. Project Identity

| Field | Value |
|---|---|
| Full Name | AI-Augmented Model-Driven Microservice Architecture Generator |
| Short Name | M²AG |
| Academic Frame | IDM / MDE (Ingénierie Dirigée par les Modèles) |
| Core Demonstration | Complete MDE lifecycle: abstract architectural model → executable distributed system |

**Thesis sentence for all academic framing:**
> "We transform abstract architectural models into executable distributed systems — not code, models."

---

## 2. Locked Architectural Decisions

These decisions are final. Do not propose alternatives unless a constraint explicitly blocks implementation.

| Decision | Choice | Reason |
|---|---|---|
| Metamodeling | EMF / Ecore | MOF-compliant, academic standard |
| Serialization | XMI | Native EMF format |
| Validation | OCL | Standard constraint language for MDE |
| Model-to-Model Transform | ATL | Industry standard M2M for EMF |
| Code Generation (M2T) | Acceleo | EMF-native, template-based |
| Backend generation target | Spring Boot + Maven only | Single stack; no Node.js/Express in MVP |
| Intermediate metamodel | `Deployment.ecore` | Mandatory; separates logical from runtime semantics |
| Visualization | React Flow or GLSP | Optional; post-MVP |
| Primary IDE (MDE core) | Classic Eclipse (Eclipse Modeling Tools) | Mature EMF/ATL/Acceleo/OCL toolchain |
| Secondary IDE (orchestration) | Eclipse Theia + Claude Code | AI-assisted generation logic after core MDE is stable |
| Container | Docker Compose | MVP scope; Kubernetes is optional advanced |

**Tooling strategy**: build and validate the entire MDE core (metamodels, ATL, Acceleo, OCL) inside Classic Eclipse first. Only then open the repository in Theia + Claude Code for pipeline orchestration and generation logic. Do not invert this order. Theia is the shell integration layer, not the foundation.

---

## 3. MDE Layer Mapping (Academically Critical)

Every implementation decision must be traceable to this stack. This is the primary academic demonstration.

| Layer | Label | Technology | Artifact |
|---|---|---|---|
| M3 | Meta-Metamodel | Ecore / MOF via EMF | Ecore infrastructure |
| M2 | Architecture Metamodel | `MicroserviceArchitecture.ecore` | Logical domain metamodel |
| M2 | Deployment Metamodel | `Deployment.ecore` | Runtime topology metamodel |
| M1 | Concrete Architecture Model | `ecommerce.xmi` | System instance (logical) |
| M1 | Concrete Deployment Model | `ecommerce-deployment.xmi` | System instance (runtime) — ATL output |
| M0 | Runtime | Generated Docker containers, Spring Boot APIs | Executable infrastructure |

**Rule**: every generated artifact must be traceable to its M1 model element via a named transformation rule and a named generation template. No manual artifact creation outside the pipeline.

---

## 4. Architecture Metamodel Specification (M2)

**File**: `metamodel/MicroserviceArchitecture.ecore`

### 4.1 Class Hierarchy

```
SystemArchitecture
├── name            : EString
├── version         : EString
├── services        : Microservice [1..*]  (containment)
├── databases       : Database [0..*]      (containment)
├── gateways        : Gateway [0..*]       (containment)
└── messageBrokers  : MessageBroker [0..*] (containment)

Microservice
├── name               : EString
├── port               : EInt
├── providedInterfaces : ServiceInterface [0..*]  (containment)
├── requiredInterfaces : ServiceInterface [0..*]  (reference)
├── dependencies       : ServiceDependency [0..*] (containment)
└── database           : Database [0..1]          (reference)

ServiceInterface
├── name      : EString
└── endpoints : APIEndpoint [1..*]  (containment)

APIEndpoint
├── path         : EString
├── method       : HttpMethod (enum)
└── authRequired : EBoolean

Database
├── type : DBType (enum)
├── host : EString
└── port : EInt

ServiceDependency
├── target   : ServiceInterface (reference)
└── protocol : Protocol (enum)

Gateway
├── name : EString
└── port : EInt

MessageBroker
├── type : BrokerType (enum)
└── port : EInt

EnvironmentVariable
├── key   : EString
└── value : EString

DeploymentConfig
├── replicas    : EInt
└── memoryLimit : EString
```

**Key design decision**: `ServiceDependency.target` points to a `ServiceInterface`, not directly to a `Microservice`. This enforces interface-based dependency modeling, cleaner ATL transformation rules, and proper separation of contracts from implementations.

### 4.2 Enumerations

Define as `EEnum` in Ecore:

| Enum | Values |
|---|---|
| `HttpMethod` | GET, POST, PUT, DELETE, PATCH |
| `DBType` | POSTGRESQL, MYSQL, MONGODB, REDIS |
| `Protocol` | REST, GRPC, EVENT |
| `BrokerType` | KAFKA, RABBITMQ |

**Note**: `Language` and `Framework` enums are removed from MVP. Backend generation is locked to Java/Spring Boot. Introducing polymorphic backend targets is an advanced post-MVP concern; including dead enum branches in the metamodel reduces semantic precision without academic value.

---

## 5. Deployment Metamodel Specification (M2)

**File**: `metamodel/Deployment.ecore`

This is the intermediate metamodel. The ATL transformation `architecture2deployment.atl` maps `MicroserviceArchitecture` instances to `Deployment` instances. Acceleo then generates artifacts from the `Deployment` model, not directly from the architecture model.

### 5.1 Class Hierarchy

```
DeploymentTopology
└── containers  : Container [1..*]  (containment)
└── networks    : NetworkLink [0..*] (containment)
└── volumes     : Volume [0..*]      (containment)

Container
├── name         : EString
├── image        : EString         (resolved docker image name)
├── buildContext : EString         (relative path for build:, if applicable)
├── portBindings : PortBinding [0..*] (containment)
├── envBindings  : EnvironmentBinding [0..*] (containment)
└── dependsOn    : Container [0..*] (reference)

PortBinding
├── hostPort      : EInt
└── containerPort : EInt

EnvironmentBinding
├── key   : EString
└── value : EString

NetworkLink
├── source : Container (reference)
└── target : Container (reference)

Volume
├── name      : EString
└── mountPath : EString
```

### 5.2 Architecture → Deployment Mapping

| Architecture element | Deployment element |
|---|---|
| `Microservice` | `Container` (buildContext = `../services/{name}`) |
| `Database` | `Container` (image = resolved from `DBType`) |
| `ServiceDependency` | `NetworkLink` + `dependsOn` reference on source Container |
| `Microservice.port` | `PortBinding { hostPort = port, containerPort = port }` |
| `EnvironmentVariable` | `EnvironmentBinding` |

**DBType → Docker image mapping** (hardcoded in ATL helper):

| DBType | Docker image |
|---|---|
| POSTGRESQL | `postgres:15-alpine` |
| MYSQL | `mysql:8.0` |
| MONGODB | `mongo:6.0` |
| REDIS | `redis:7-alpine` |

---

## 6. Validation Rules (OCL)

**File**: `metamodel/constraints.ocl`

All 8 rules run before any transformation is triggered. A failed constraint blocks the pipeline and returns a structured `ValidationResult { valid: boolean, violations: Violation[] }` where `Violation { rule: String, elementId: String, message: String }`.

### Rule 1 — Unique Service Ports

```ocl
context Microservice
inv UniquePort:
  Microservice.allInstances()
    ->select(s | s.port = self.port)
    ->size() = 1
```

### Rule 2 — Unique Service Names

```ocl
context Microservice
inv UniqueServiceName:
  Microservice.allInstances()->isUnique(s | s.name)
```

Prevents silent directory overwrite during generation.

### Rule 3 — No Self-Dependency

```ocl
context Microservice
inv NoSelfDependency:
  not self.dependencies
    ->collect(d | d.target)
    ->collect(i | Microservice.allInstances()->select(m | m.providedInterfaces->includes(i)))
    ->flatten()
    ->includes(self)
```

### Rule 4 — Valid API Path Format

```ocl
context APIEndpoint
inv ValidPath:
  self.path.startsWith('/')
```

### Rule 5 — No Duplicate API Routes per Interface

```ocl
context ServiceInterface
inv NoDuplicateRoutes:
  self.endpoints->isUnique(e | e.path + e.method.toString())
```

### Rule 6 — Service Has At Least One Provided Interface

```ocl
context Microservice
inv HasProvidedInterface:
  self.providedInterfaces->size() >= 1
```

### Rule 7 — Database Port Uniqueness

```ocl
context Database
inv UniqueDBPort:
  Database.allInstances()
    ->select(d | d.port = self.port)
    ->size() = 1
```

### Rule 8 — Dependency Target Has Valid Port

```ocl
context ServiceDependency
inv TargetHasPort:
  Microservice.allInstances()
    ->select(m | m.providedInterfaces->includes(self.target))
    ->forAll(m | m.port > 0)
```

---

## 7. Transformation Pipeline

**Directory**: `transformations/`

### Full Pipeline Sequence

```
[M1: ecommerce.xmi]              (Architecture instance — logical)
        |
        v
[OCL Validation]  -- FAIL --> ValidationResult { violations }, halt
        |
       PASS
        |
        v  ATL: architecture2deployment.atl
[M1: ecommerce-deployment.xmi]   (Deployment instance — runtime topology)
        |
        +-----------+------------------+
        |           |                  |
        v           v                  v
  Acceleo       Acceleo           ATL: arch2openapi.atl
DockerCompose  SpringBoot              |
   .mtl          .mtl                 v
        |           |            Acceleo OpenAPI.mtl
        v           v                  |
docker-compose  services/*        openapi/*.yaml
  .yml          Dockerfile
                Controller.java
                ...
```

This is the academically correct sequence: M2M transformation first, then M2T generation from the derived model. Acceleo templates read from `Deployment.ecore` instances for infrastructure artifacts; they read from `MicroserviceArchitecture.ecore` instances for OpenAPI (API contracts are a logical concern, not a deployment concern).

### T1 — Architecture → Deployment (M2M, ATL)

**File**: `transformations/architecture2deployment.atl`

```atl
module architecture2deployment;
create OUT : Deployment from IN : MicroserviceArchitecture;

rule MicroserviceToContainer {
  from s : MicroserviceArchitecture!Microservice
  to c : Deployment!Container (
    name <- s.name,
    buildContext <- '../services/' + s.name,
    portBindings <- s.port > 0 then Sequence{thisModule.makePortBinding(s.port)} else Sequence{}
  )
}

rule DatabaseToContainer {
  from db : MicroserviceArchitecture!Database
  to c : Deployment!Container (
    name <- db.type.toString().toLower() + '-' + db.port.toString(),
    image <- thisModule.resolveImage(db.type),
    portBindings <- Sequence{thisModule.makePortBinding(db.port)}
  )
}

-- ServiceDependency → NetworkLink + dependsOn population
-- EnvironmentVariable → EnvironmentBinding
-- lazy rules for PortBinding, EnvironmentBinding, NetworkLink
```

ATL helpers must implement `resolveImage(DBType)` returning the image string, and `makePortBinding(port)` creating a symmetric `PortBinding`.

### T2 — Architecture → OpenAPI Model (M2M, ATL)

**File**: `transformations/architecture2openapi.atl`

Maps:
- `Microservice` → OpenAPI document root (`info`, `servers`)
- `ServiceInterface.endpoints` → `paths` entries
- `HttpMethod` → HTTP operation verb
- `APIEndpoint.authRequired = true` → `security: [{ bearerAuth: [] }]` reference

### T3 — Architecture → Kubernetes (M2M, ATL) [OPTIONAL]

**File**: `transformations/architecture2k8s.atl`

Maps `DeploymentTopology.Container` → K8s `Deployment` + `Service` manifest. Input is `Deployment.ecore` instance, not the architecture model directly.

---

## 8. Code Generation Targets (Acceleo)

**Directory**: `generators/acceleo/`

Acceleo templates consume M1 instances conforming to either `MicroserviceArchitecture.ecore` or `Deployment.ecore` as specified.

### 8.1 Spring Boot Service Skeleton

**Input metamodel**: `MicroserviceArchitecture.ecore`  
**Template**: `generators/acceleo/SpringBootService.mtl`

For each `Microservice`, generate:

```
generated/services/{service.name}/
├── pom.xml
│     (groupId: mg.codel.m2ag, artifactId: {name}-service,
│      Spring Boot 3.x, port from model, Maven)
├── src/main/java/mg/codel/m2ag/{name}/
│   ├── {Name}Application.java
│   ├── controller/
│   │   └── {Name}Controller.java
│   │         (one @RequestMapping method per APIEndpoint,
│   │          return type ResponseEntity<?>,
│   │          body: TODO stub + traceability comment)
│   └── service/
│       └── {Name}Service.java
├── src/main/resources/
│   └── application.properties
│         (server.port={service.port})
└── Dockerfile
      (FROM eclipse-temurin:17-jre, EXPOSE {port}, ENTRYPOINT java -jar)
```

**Traceability comment in each generated file** (see Section 10):
```java
// [M2AG-TRACE] Generated from: Microservice({service.name})
// [M2AG-TRACE] Template: SpringBootService.mtl
// [M2AG-TRACE] Model: {input.xmi.filename}
```

### 8.2 Docker Compose

**Input metamodel**: `Deployment.ecore`  
**Template**: `generators/acceleo/DockerCompose.mtl`  
**Output**: `generated/docker/docker-compose.yml`

```yaml
version: '3.8'
services:
  {container.name}:
    build: {container.buildContext}       # for service containers
    image: {container.image}              # for database containers
    ports:
      - "{portBinding.hostPort}:{portBinding.containerPort}"
    depends_on:
      - {dependsOn.name}
    environment:
      {envBinding.key}: {envBinding.value}
```

### 8.3 OpenAPI Specification

**Input metamodel**: `MicroserviceArchitecture.ecore`  
**Template**: `generators/acceleo/OpenAPI.mtl`  
**Output**: `generated/openapi/{service.name}-openapi.yaml`

One file per `Microservice`. Derives paths from all `providedInterfaces[*].endpoints`.

---

## 9. Traceability Strategy

Traceability is a first-class MDE requirement. Every generated artifact must carry explicit back-references to its source model element, its transformation rule, and its generation template.

### 9.1 Traceability Header (Source Files)

All generated Java files include a traceability block at the top:

```java
// ============================================================
// [M2AG-TRACE] Source element : Microservice({name})
// [M2AG-TRACE] Transformation : architecture2deployment.atl
// [M2AG-TRACE] Template       : SpringBootService.mtl
// [M2AG-TRACE] Input model    : models/ecommerce.xmi
// [M2AG-TRACE] Generated at   : {timestamp}
// DO NOT EDIT — regenerate from model
// ============================================================
```

### 9.2 Traceability in YAML / Properties Files

```yaml
# [M2AG-TRACE] Source: DeploymentTopology / Container({name})
# [M2AG-TRACE] Template: DockerCompose.mtl
```

### 9.3 Traceability Map File

`GenerationPipeline.java` writes a `generated/traceability.json` after each run:

```json
{
  "model": "models/ecommerce.xmi",
  "generatedAt": "ISO-8601 timestamp",
  "artifacts": [
    {
      "file": "generated/services/AuthService/controller/AuthServiceController.java",
      "sourceElement": "Microservice(AuthService)",
      "transformation": "architecture2deployment.atl",
      "template": "SpringBootService.mtl"
    }
  ]
}
```

This file has direct academic value during presentation: it demonstrates that generation is model-driven and reproducible, not hand-coded.

---

## 10. Reference Model Instances (M1)

**Directory**: `models/`

### 10.1 E-Commerce Architecture Model

**File**: `models/ecommerce.xmi`

```
SystemArchitecture { name="ECommerceSystem", version="1.0" }

Microservice(AuthService, port=8081)
  providedInterfaces:
    ServiceInterface(AuthAPI)
      APIEndpoint(POST, /auth/login,   authRequired=false)
      APIEndpoint(POST, /auth/register, authRequired=false)

Microservice(ProductService, port=8082)
  providedInterfaces:
    ServiceInterface(ProductAPI)
      APIEndpoint(GET,  /products,        authRequired=true)
      APIEndpoint(POST, /products,        authRequired=true)
      APIEndpoint(GET,  /products/{id},   authRequired=false)
  dependencies:
    ServiceDependency(target=AuthAPI, protocol=REST)

Microservice(PaymentService, port=8083)
  providedInterfaces:
    ServiceInterface(PaymentAPI)
      APIEndpoint(POST, /payments,        authRequired=true)
      APIEndpoint(GET,  /payments/{id},   authRequired=true)
  dependencies:
    ServiceDependency(target=AuthAPI, protocol=REST)

Database(AuthDB,     type=POSTGRESQL, host=localhost, port=5432)
Database(ProductDB,  type=POSTGRESQL, host=localhost, port=5433)
Database(PaymentDB,  type=POSTGRESQL, host=localhost, port=5434)

AuthService.database    → AuthDB
ProductService.database → ProductDB
PaymentService.database → PaymentDB
```

### 10.2 Banking System (optional second model)

**File**: `models/banking.xmi`

Services: `AccountService`, `TransferService`, `NotificationService`  
Add one `MessageBroker { type=KAFKA, port=9092 }` and one event-protocol `ServiceDependency` to demonstrate broker modeling.

---

## 11. Repository Structure

```
m2ag/
├── metamodel/
│   ├── MicroserviceArchitecture.ecore     # M2: logical architecture metamodel
│   ├── Deployment.ecore                   # M2: runtime deployment metamodel
│   └── constraints.ocl                   # OCL: 8 validation rules
│
├── models/
│   ├── ecommerce.xmi                     # M1: architecture instance (input)
│   ├── ecommerce-deployment.xmi          # M1: deployment instance (ATL output)
│   └── banking.xmi                       # M1: optional second model
│
├── transformations/
│   ├── architecture2deployment.atl       # M2M: logical → runtime topology
│   ├── architecture2openapi.atl          # M2M: API model → OpenAPI model
│   └── architecture2k8s.atl             # M2M: deployment → K8s (optional)
│
├── generators/
│   ├── acceleo/
│   │   ├── SpringBootService.mtl         # M2T: Spring Boot skeleton (from arch model)
│   │   ├── DockerCompose.mtl             # M2T: docker-compose.yml (from deploy model)
│   │   └── OpenAPI.mtl                   # M2T: OpenAPI YAML (from arch model)
│   └── templates/                        # Static template fragments (pom.xml base, etc.)
│
├── generated/                            # M0: all generated artifacts
│   ├── services/
│   │   ├── AuthService/
│   │   ├── ProductService/
│   │   └── PaymentService/
│   ├── docker/
│   │   └── docker-compose.yml
│   ├── openapi/
│   │   ├── AuthService-openapi.yaml
│   │   ├── ProductService-openapi.yaml
│   │   └── PaymentService-openapi.yaml
│   ├── kubernetes/                       # optional
│   └── traceability.json                 # artifact → model element map
│
├── validation/
│   └── ValidationEngine.java             # OCL runner: loads constraints.ocl, evaluates all rules
│
├── pipeline/
│   └── GenerationPipeline.java           # Entry point: load XMI → validate → T1 → T2/T3 → write traceability
│
├── theia-extension/                      # Post-MVP: IDE shell integration
│   └── src/
│
└── docs/
    ├── M2AG_CDC_CLAUDE_INSTRUCTIONS.md   # This file
    ├── mde-layer-mapping.md
    └── architecture-diagram.png
```

---

## 12. Implementation Priority Order

Phase-gated. Do not advance until the current phase produces testable, correct output.

| Phase | Deliverable | Acceptance Criterion |
|---|---|---|
| 1 | `MicroserviceArchitecture.ecore` | Loads in Eclipse EMF; all classes, enums, references defined; no Ecore validation warnings |
| 2 | `Deployment.ecore` | Loads in Eclipse EMF; all Container/Network/Volume classes defined |
| 3 | `ecommerce.xmi` | Valid instance conforming to `MicroserviceArchitecture.ecore`; passes EMF consistency check |
| 4 | `constraints.ocl` | All 8 rules parse and evaluate; tested against valid XMI (all pass) and invalid XMI (targeted violations) |
| 5 | `architecture2deployment.atl` | Transforms `ecommerce.xmi` into a valid `ecommerce-deployment.xmi` conforming to `Deployment.ecore` |
| 6 | `DockerCompose.mtl` | Generates syntactically valid `docker-compose.yml` from `ecommerce-deployment.xmi` |
| 7 | `SpringBootService.mtl` | Generates 3 compilable (stub-level) Spring Boot projects with traceability headers |
| 8 | `architecture2openapi.atl` + `OpenAPI.mtl` | Generates valid OpenAPI 3.0 YAML per service |
| 9 | `GenerationPipeline.java` | Single entry point: load XMI → validate → ATL → Acceleo → write `traceability.json` |
| 10 | Visualization (React Flow) | Renders service/dependency graph from XMI in browser |
| 11 | Theia extension | IDE shell integration; post-MVP |

---

## 13. Failure Modes and Handling

| Failure | Handling |
|---|---|
| OCL constraint violation | Return `ValidationResult { valid=false, violations: [{ rule, elementId, message }] }`. Halt pipeline before any transformation. |
| XMI does not conform to metamodel | Catch EMF `Resource.load()` diagnostic errors; report file + diagnostic message |
| ATL transform error | Wrap ATL execution; surface ATL module name, rule name, and offending input element |
| Acceleo generation error | Catch template evaluation exception; report template file, line, and model element |
| Port conflict (services) | OCL Rule 1 catches it before generation; never reaches Docker |
| Name collision (services) | OCL Rule 2 catches it; prevents silent directory overwrite |
| Transitive circular dependency | OCL Rule 3 catches direct self-reference; for transitive cycles, implement DFS in `ValidationEngine.checkTransitiveCycles()` over the `ServiceDependency` graph |
| Missing dependency target port | OCL Rule 8 catches it at validation time |
| ATL/EMF version mismatch | Document the exact Eclipse Modeling Tools version used; pin it in `docs/toolchain.md` |

---

## 14. Claude Code Integration Points

Claude Code operates as AI Architecture Copilot after the core MDE toolchain is validated in Classic Eclipse.

| Phase | Claude Code Role |
|---|---|
| Ecore authoring | Generate Ecore XML for both `MicroserviceArchitecture.ecore` and `Deployment.ecore`; validate structure against EMF schema |
| OCL rules | Write all 8 rule bodies; debug `OCLHelper` API calls in `ValidationEngine.java` |
| ATL — `architecture2deployment.atl` | Write matched rules, lazy rules, and helper functions; debug rule firing order |
| ATL — `architecture2openapi.atl` | Write path/method mapping rules |
| Acceleo — `DockerCompose.mtl` | Write `[for]` loops over `Container` collection; verify `dependsOn` ordering |
| Acceleo — `SpringBootService.mtl` | Generate pom.xml template, controller stub template, traceability header injection |
| `ValidationEngine.java` | Implement OCL executor, DFS cycle detector, `ValidationResult` builder |
| `GenerationPipeline.java` | Orchestrate full pipeline; write `traceability.json`; structured error propagation |
| `traceability.json` | Define schema; implement writer in pipeline |

**Scope boundary**: Claude Code resolves logic, structure, and code generation. It does not resolve EMF classpath issues, ATL runtime OSGi conflicts, or Eclipse plugin version incompatibilities. Those are toolchain problems resolved in Classic Eclipse before Theia is involved.

---

## 15. MVP Checklist

Every item is mandatory before any optional work begins.

- [ ] `MicroserviceArchitecture.ecore` — complete logical metamodel with `ServiceInterface`
- [ ] `Deployment.ecore` — runtime topology metamodel
- [ ] `ecommerce.xmi` — valid architecture model instance
- [ ] `constraints.ocl` — 8 rules, tested on valid and invalid inputs
- [ ] `architecture2deployment.atl` — produces valid `ecommerce-deployment.xmi`
- [ ] `DockerCompose.mtl` — generates valid `docker-compose.yml` from deployment model
- [ ] `SpringBootService.mtl` — generates 3 compilable service skeletons with traceability headers
- [ ] `architecture2openapi.atl` + `OpenAPI.mtl` — generates valid OpenAPI 3.0 YAML per service
- [ ] `GenerationPipeline.java` — single entry point, full pipeline, writes `traceability.json`
- [ ] End-to-end demo: `ecommerce.xmi` in → full artifact set in `/generated/` out

---

## 16. Out of Scope (MVP)

Do not implement unless all 10 MVP checklist items are complete and verified.

- Kubernetes manifests
- Event-driven / Kafka modeling
- GraphQL generation
- Service mesh modeling
- Theia IDE extension / plugin UI
- Deployment simulation
- Reverse engineering (existing system → model)
- AI-generated architecture suggestions
- Live architecture graph with hot reload
- Multi-backend polymorphism (Node.js/Express targets)
