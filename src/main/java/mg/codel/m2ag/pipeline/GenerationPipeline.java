package mg.codel.m2ag.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;

import mg.codel.m2ag.validation.ValidationEngine;
import mg.codel.m2ag.validation.ValidationResult;
import mg.codel.m2ag.validation.Violation;

/**
 * Single entry point of the M2AG pipeline (CDC phase 9 / section 7).
 * <p>Sequence: load XMI &rarr; OCL validation &rarr; M2M transform (ATL) &rarr;
 * M2T generation (Acceleo) &rarr; write {@code traceability.json}. The M2M and
 * M2T stages are headless executable mirrors of
 * {@code transformations/architecture2deployment.atl} and the
 * {@code generators/acceleo/*.mtl} templates, which remain the authoritative
 * model-driven definitions run in Classic Eclipse (CDC phases 5-8).
 *
 * <p>{@link #execute} is the reusable core (used by both this CLI and the web
 * server); {@link #main}/{@link #run} add console logging and exit codes.
 * Usage: {@code mvn exec:java -Dexec.args="models/ecommerce.xmi"}.
 */
public final class GenerationPipeline {

    /** Raised when a constraint violation halts the pipeline before transformation. */
    public static final class PipelineException extends Exception {
        public PipelineException(String message) {
            super(message);
        }
    }

    /**
     * Structured result of a pipeline run.
     *
     * @param valid           whether the model passed all OCL constraints
     * @param violations      the violations (empty when valid)
     * @param deploymentModel path of the derived deployment XMI, or {@code null} when invalid
     * @param artifactCount   number of generated artifacts (0 when invalid)
     */
    public record Outcome(boolean valid, List<Violation> violations,
                          Path deploymentModel, int artifactCount) {
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

    /** CLI wrapper: logs each stage and turns an invalid model into a halt. */
    public void run(Path projectRoot, Path modelFile) throws Exception {
        System.out.println("=== M2AG generation pipeline ===");
        System.out.println("input model : " + projectRoot.relativize(modelFile));
        Outcome outcome = execute(projectRoot, modelFile);
        if (!outcome.valid()) {
            outcome.violations().forEach(v -> System.err.println("    " + v));
            throw new PipelineException(outcome.violations().size() + " constraint violation(s)");
        }
        System.out.println("=== pipeline complete (" + outcome.artifactCount() + " artifacts) ===");
    }

    /**
     * Runs the full pipeline and returns a structured {@link Outcome} without
     * throwing on validation failure or calling {@code System.exit}. Always
     * writes {@code generated/validation-report.json}; on a valid model it also
     * writes the deployment XMI, all M2T artifacts, and {@code traceability.json}.
     */
    public Outcome execute(Path projectRoot, Path modelFile) throws Exception {
        Path metamodelDir = projectRoot.resolve("metamodel");
        Path modelsDir = projectRoot.resolve("models");
        Path generatedDir = projectRoot.resolve("generated");

        // 1. Load the M2 metamodels and the M1 model instance.
        ModelLoader loader = new ModelLoader();
        loader.registerMetamodel(metamodelDir.resolve("MicroserviceArchitecture.ecore"));
        EPackage deploymentMetamodel =
                loader.registerMetamodel(metamodelDir.resolve("Deployment.ecore"));
        EObject architecture = loader.loadModel(modelFile);
        System.out.println("[1] loaded    : SystemArchitecture("
                + Emf.str(architecture, "name") + ")");

        // 2. OCL validation - report written whether it passes or fails. The
        //    output directory is cleared first so results reflect only this run.
        Files.createDirectories(generatedDir);
        clearDirectory(generatedDir);
        ValidationResult validation = new ValidationEngine(architecture).validate();
        Files.writeString(generatedDir.resolve("validation-report.json"), validation.toJson());
        if (!validation.isValid()) {
            System.out.println("[2] validated : FAIL ("
                    + validation.getViolations().size() + " violations) - halting");
            return new Outcome(false, validation.getViolations(), null, 0);
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
        TraceabilityWriter trace = new TraceabilityWriter();
        new DockerComposeGenerator().generate(topology, generatedDir, trace);
        new SpringBootGenerator().generate(architecture, generatedDir, trace);
        new OpenApiGenerator().generate(architecture, generatedDir, trace);

        // 5. Traceability map.
        trace.write(generatedDir.resolve("traceability.json"),
                projectRoot.relativize(modelFile).toString());
        System.out.println("[4] M2T (MTL) : " + trace.size() + " artifacts in "
                + projectRoot.relativize(generatedDir));

        return new Outcome(true, List.of(), deploymentModel, trace.size());
    }

    /** Deletes the contents of a directory (kept), so a run leaves only its own output. */
    private static void clearDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
        }
    }
}
