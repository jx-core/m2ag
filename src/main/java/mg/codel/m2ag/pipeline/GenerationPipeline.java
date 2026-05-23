package mg.codel.m2ag.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;

import mg.codel.m2ag.validation.ValidationEngine;
import mg.codel.m2ag.validation.ValidationResult;

/**
 * Single entry point of the M2AG pipeline (CDC phase 9 / section 7).
 * <p>Sequence: load XMI &rarr; OCL validation &rarr; M2M transform (ATL) &rarr;
 * M2T generation (Acceleo) &rarr; write {@code traceability.json}. The M2M and
 * M2T stages are headless executable mirrors of
 * {@code transformations/architecture2deployment.atl} and the
 * {@code generators/acceleo/*.mtl} templates, which remain the authoritative
 * model-driven definitions run in Classic Eclipse (CDC phases 5-8).
 *
 * <p>Usage: {@code mvn exec:java -Dexec.args="models/ecommerce.xmi"}.
 * The argument defaults to {@code models/ecommerce.xmi}.
 */
public final class GenerationPipeline {

    /** Raised when a constraint violation halts the pipeline before transformation. */
    public static final class PipelineException extends Exception {
        public PipelineException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        Path projectRoot = Path.of("").toAbsolutePath();
        Path modelFile = (args.length > 0 ? Path.of(args[0]) : projectRoot.resolve("models/ecommerce.xmi"))
                .toAbsolutePath();
        try {
            new GenerationPipeline().run(projectRoot, modelFile);
        } catch (PipelineException e) {
            System.err.println("PIPELINE HALTED: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("PIPELINE ERROR: " + e);
            e.printStackTrace();
            System.exit(2);
        }
    }

    public void run(Path projectRoot, Path modelFile) throws Exception {
        Path metamodelDir = projectRoot.resolve("metamodel");
        Path modelsDir = projectRoot.resolve("models");
        Path generatedDir = projectRoot.resolve("generated");

        System.out.println("=== M2AG generation pipeline ===");
        System.out.println("input model : " + projectRoot.relativize(modelFile));

        // 1. Load the M2 metamodels and the M1 model instance.
        ModelLoader loader = new ModelLoader();
        loader.registerMetamodel(metamodelDir.resolve("MicroserviceArchitecture.ecore"));
        EPackage deploymentMetamodel =
                loader.registerMetamodel(metamodelDir.resolve("Deployment.ecore"));
        EObject architecture = loader.loadModel(modelFile);
        System.out.println("[1] loaded    : SystemArchitecture("
                + Emf.str(architecture, "name") + ")");

        // 2. OCL validation - halts the pipeline on any violation.
        ValidationResult validation = new ValidationEngine(architecture).validate();
        if (!validation.isValid()) {
            System.err.println(validation);
            throw new PipelineException(
                    validation.getViolations().size() + " constraint violation(s)");
        }
        System.out.println("[2] validated : PASS (8 OCL rules + transitive cycle check)");

        // 3. M2M transformation : architecture -> deployment topology.
        EObject topology =
                new DeploymentTransformer(deploymentMetamodel).transform(architecture);
        String baseName = modelFile.getFileName().toString().replaceFirst("\\.xmi$", "");
        Path deploymentModel = modelsDir.resolve(baseName + "-deployment.xmi");
        Resource deploymentResource = loader.createResource(deploymentModel);
        deploymentResource.getContents().add(topology);
        deploymentResource.save(null);
        System.out.println("[3] M2M (ATL) : " + projectRoot.relativize(deploymentModel));

        // 4. M2T generation : docker-compose, Spring Boot services, OpenAPI specs.
        Files.createDirectories(generatedDir);
        TraceabilityWriter trace = new TraceabilityWriter();
        new DockerComposeGenerator().generate(topology, generatedDir, trace);
        new SpringBootGenerator().generate(architecture, generatedDir, trace);
        new OpenApiGenerator().generate(architecture, generatedDir, trace);
        System.out.println("[4] M2T (MTL) : " + trace.size() + " artifacts in "
                + projectRoot.relativize(generatedDir));

        // 5. Traceability map.
        Path traceFile = generatedDir.resolve("traceability.json");
        trace.write(traceFile, projectRoot.relativize(modelFile).toString());
        System.out.println("[5] traceable : " + projectRoot.relativize(traceFile));

        System.out.println("=== pipeline complete ===");
    }
}
