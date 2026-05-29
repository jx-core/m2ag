package mg.codel.m2ag.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
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

    /** CLI wrapper: streams the log to the console, turns an invalid model into a halt. */
    public void run(Path projectRoot, Path modelFile) throws Exception {
        Outcome outcome = execute(projectRoot, modelFile, System.out::println);
        if (!outcome.valid()) {
            throw new PipelineException(outcome.violations().size() + " constraint violation(s)");
        }
    }

    /** Convenience overload that logs to {@code System.out}. */
    public Outcome execute(Path projectRoot, Path modelFile) throws Exception {
        return execute(projectRoot, modelFile, System.out::println);
    }

    /**
     * Runs the full pipeline and returns a structured {@link Outcome} without
     * throwing on validation failure or calling {@code System.exit}. Every
     * stage and every generated artifact is reported to {@code log} (the CLI
     * passes {@code System.out::println}; the web server streams it live to the
     * browser). Always writes {@code generated/validation-report.json}; on a
     * valid model it also writes the deployment XMI, all M2T artifacts, and
     * {@code traceability.json}.
     */
    public Outcome execute(Path projectRoot, Path modelFile, Consumer<String> log) throws Exception {
        Path metamodelDir = projectRoot.resolve("metamodel");
        Path modelsDir = projectRoot.resolve("models");
        Path generatedDir = projectRoot.resolve("generated");

        log.accept("=== M2AG generation pipeline ===");
        log.accept("input model : " + projectRoot.relativize(modelFile));

        // 1. Load the M2 metamodels and the M1 model instance.
        ModelLoader loader = new ModelLoader();
        loader.registerMetamodel(metamodelDir.resolve("MicroserviceArchitecture.ecore"));
        EPackage deploymentMetamodel =
                loader.registerMetamodel(metamodelDir.resolve("Deployment.ecore"));
        EObject architecture = loader.loadModel(modelFile);
        log.accept("[1] loaded    : SystemArchitecture(" + Emf.str(architecture, "name") + ")");

        // 2. OCL validation - report written whether it passes or fails. The
        //    output directory is cleared first so results reflect only this run.
        Files.createDirectories(generatedDir);
        clearDirectory(generatedDir);
        ValidationResult validation = new ValidationEngine(architecture).validate();
        Files.writeString(generatedDir.resolve("validation-report.json"), validation.toJson());
        if (!validation.isValid()) {
            log.accept("[2] validated : FAIL - " + validation.getViolations().size()
                    + " violation(s):");
            validation.getViolations().forEach(v -> log.accept("      - " + v));
            log.accept("=== pipeline halted (model invalid) ===");
            return new Outcome(false, validation.getViolations(), null, 0);
        }
        log.accept("[2] validated : PASS (8 OCL rules + transitive cycle check)");

        // 3. M2M transformation : architecture -> deployment topology.
        EObject topology =
                new DeploymentTransformer(deploymentMetamodel).transform(architecture);
        String baseName = modelFile.getFileName().toString().replaceFirst("\\.xmi$", "");
        Path deploymentModel = modelsDir.resolve(baseName + "-deployment.xmi");
        Resource deploymentResource = loader.createResource(deploymentModel);
        deploymentResource.getContents().add(topology);
        deploymentResource.save(null);
        log.accept("[3] M2M (ATL) : " + Emf.list(topology, "containers").size()
                + " containers -> " + projectRoot.relativize(deploymentModel));

        // 4. M2T generation : docker-compose, Spring Boot services, OpenAPI specs.
        TraceabilityWriter trace = new TraceabilityWriter();
        int mark = trace.size();
        new DockerComposeGenerator().generate(topology, generatedDir, trace);
        logNewArtifacts(trace, mark, log);
        mark = trace.size();
        new SpringBootGenerator().generate(architecture, generatedDir, trace);
        logNewArtifacts(trace, mark, log);
        mark = trace.size();
        new OpenApiGenerator().generate(architecture, generatedDir, trace);
        logNewArtifacts(trace, mark, log);

        // 5. Traceability map.
        trace.write(generatedDir.resolve("traceability.json"),
                projectRoot.relativize(modelFile).toString());
        log.accept("[4] M2T (MTL) : " + trace.size() + " artifacts + traceability.json");
        log.accept("=== pipeline complete (" + trace.size() + " artifacts) ===");

        return new Outcome(true, List.of(), deploymentModel, trace.size());
    }

    private static void logNewArtifacts(TraceabilityWriter trace, int from, Consumer<String> log) {
        List<TraceabilityWriter.Entry> entries = trace.entries();
        for (int i = from; i < entries.size(); i++) {
            log.accept("      + " + entries.get(i).file());
        }
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
