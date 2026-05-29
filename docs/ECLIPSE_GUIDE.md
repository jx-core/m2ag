# Beginner's guide — running M²AG in Classic Eclipse

This guide is for **authoring and academically proving** the model-driven
artifacts (`.ecore`, `.ocl`, `.atl`, `.mtl`) in Eclipse, which is the mature
toolchain for EMF / OCL / ATL / Acceleo.

> **You do not need Eclipse to *run* the project.** The pipeline already runs
> headless (`mvn exec:java`), in the browser (the web app), and from the IDE
> extension. Use Eclipse only when you want to *show* the models in the
> "official" MDE tooling — the graphical Ecore editor, the interactive OCL
> console, and the ATL/Acceleo launchers. If your course doesn't require that,
> you can skip this entirely.

Menu labels vary slightly between Eclipse versions; the paths below are the
canonical ones. Pin the versions you use in [toolchain.md](toolchain.md) once
it works (CDC §13 warns MDE tool versions are fragile — don't upgrade
mid-project).

---

## 1. Install Eclipse Modeling Tools

1. Go to <https://www.eclipse.org/downloads/packages/>.
2. Download **"Eclipse Modeling Tools"** (it bundles EMF + Ecore + OCL).
3. Unpack and launch it. When asked for a *workspace*, pick any empty folder
   (this is Eclipse's scratch area — **not** your project folder).

### Add ATL and Acceleo (one time)

EMF and OCL come with the package; ATL and Acceleo usually do not.

1. **Help → Eclipse Marketplace…**
2. Search **"ATL"** → install **"ATL - ATL Transformation Language SDK"**.
3. Search **"Acceleo"** → install **"Acceleo"**.
4. Restart Eclipse when prompted.

(If Marketplace is unavailable, use **Help → Install New Software…** with the
release update site and tick the *Modeling → ATL* and *Modeling → Acceleo*
features.)

---

## 2. Open the M²AG project

1. **File → Open Projects from File System…**
2. *Import source* → **Directory…** → select your cloned `m2ag-mde` folder.
3. Click **Finish**. The folders `metamodel/`, `models/`, `transformations/`,
   `generators/` now appear in the **Project Explorer**.

No special Eclipse project files are needed — these are plain model files.

---

## 3. Look at the metamodel (M2)

1. In `metamodel/`, double-click **`MicroserviceArchitecture.ecore`**.
2. It opens in the **Ecore tree editor**. Expand the package to see
   `SystemArchitecture`, `Microservice`, `ServiceInterface`, the enums, etc.
3. To check it: right-click the root package → **Validate**. You should get
   *"Validation completed successfully."*

Do the same for `Deployment.ecore`.

---

## 4. Validate a model with OCL (proves CDC §6)

The 8 rules live in `metamodel/constraints.ocl`. To run them interactively:

1. Open **`models/ecommerce.xmi`** with the **Sample Reflective Ecore Model
   Editor** (right-click → **Open With →** that editor).
2. Open the OCL console: **Window → Show View → Other… → OCL → Interactive
   OCL Console**.
3. In the model editor, click the root **SystemArchitecture** element (the
   console evaluates in the context of the current selection).
4. In `constraints.ocl`, copy a rule body and evaluate it, e.g. type into the
   console:

   ```ocl
   Microservice.allInstances()->isUnique(s | s.name)
   ```

   It should return `true` for `ecommerce.xmi`.
5. To see a rule *fail*, edit a copy of the model to duplicate a port, reload,
   and re-evaluate the `UniquePort` rule — it returns `false`.

This is the academic demonstration that the constraints are real OCL, not just
the Java mirror in `ValidationEngine`.

---

## 5. Run the ATL transformation (M2M, CDC §7)

Turns `ecommerce.xmi` into `ecommerce-deployment.xmi`.

1. Right-click **`transformations/architecture2deployment.atl` → Run As → Run
   Configurations…**
2. Create a new **ATL Transformation** configuration. Set:
   - **Module**: `architecture2deployment.atl`
   - **Source model `IN`**: metamodel `MicroserviceArchitecture` →
     `metamodel/MicroserviceArchitecture.ecore`; model →
     `models/ecommerce.xmi`.
   - **Target model `OUT`**: metamodel `Deployment` →
     `metamodel/Deployment.ecore`; output path →
     `models/ecommerce-deployment.xmi`.
3. Click **Run**. Open the produced `ecommerce-deployment.xmi` to see the
   `DeploymentTopology` with its `Container`s.

> The headless Java `DeploymentTransformer` mirrors this exact transformation,
> so the result matches `models/ecommerce-deployment.xmi` produced by `mvn`.

---

## 6. Generate code with Acceleo (M2T, CDC §8)

1. Make an **Acceleo project** (or register the `generators/acceleo` module):
   **File → New → Other… → Acceleo → Acceleo Project**, then copy the `.mtl`
   files in — or open the existing `.mtl` and use **Run As → Launch Acceleo
   Application**.
2. For `DockerCompose.mtl`, the input model is the **deployment** model
   (`ecommerce-deployment.xmi`); for `SpringBootService.mtl` and `OpenAPI.mtl`
   the input is the **architecture** model (`ecommerce.xmi`).
3. Set the output folder to `generated/` and run. Compare with the `generated/`
   tree produced by the Java pipeline — they should match.

---

## 7. The full picture

| You did in Eclipse | The headless equivalent |
|---|---|
| Validated the model in the OCL console | `ValidationEngine` (`mvn exec:java`) |
| Ran `architecture2deployment.atl` | `DeploymentTransformer` |
| Ran the `.mtl` templates | `DockerComposeGenerator` / `SpringBootGenerator` / `OpenApiGenerator` |

Both paths produce the same artifacts. Eclipse is where you *author and prove*
the models; the `mvn` pipeline / web app / IDE extension is how you *run* them
day to day.

---

## Troubleshooting

- **ATL run fails with a metamodel error** — double-check the `IN`/`OUT`
  metamodel bindings point at the right `.ecore` and that the nsURIs match
  (`http://m2ag.codel.mg/architecture/1.0` and `.../deployment/1.0`).
- **Acceleo can't find the metamodel** — make sure the EPackage is registered
  (open the `.ecore` once, or register it in the launch config).
- **Version conflicts (OSGi / plugin errors)** — note your exact Eclipse
  Modeling Tools version and don't upgrade ATL/Acceleo/EMF mid-project.
  Resolving these is an Eclipse-side task, separate from the model logic.
