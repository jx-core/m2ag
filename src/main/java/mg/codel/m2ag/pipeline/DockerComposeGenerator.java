package mg.codel.m2ag.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

/**
 * M2T generation stage : deployment topology &rarr; {@code docker-compose.yml}.
 * <p>Headless executable mirror of {@code generators/acceleo/DockerCompose.mtl}
 * (CDC section 8.2). The Acceleo template is the authoritative model-driven
 * definition; this class reproduces it so the pipeline runs without Acceleo.
 */
public final class DockerComposeGenerator {

    public void generate(EObject topology, Path generatedRoot, TraceabilityWriter trace)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# ============================================================\n");
        sb.append("# [M2AG-TRACE] Source   : DeploymentTopology\n");
        sb.append("# [M2AG-TRACE] Template : DockerCompose.mtl\n");
        sb.append("# DO NOT EDIT - regenerate from the model\n");
        sb.append("# ============================================================\n");
        sb.append("version: '3.8'\n\n");
        sb.append("services:\n");

        for (EObject container : Emf.list(topology, "containers")) {
            sb.append("  ").append(Emf.str(container, "name")).append(":\n");

            String buildContext = Emf.str(container, "buildContext");
            if (buildContext == null || buildContext.isEmpty()) {
                sb.append("    image: ").append(Emf.str(container, "image")).append('\n');
            } else {
                sb.append("    build: ").append(buildContext).append('\n');
            }

            List<EObject> ports = Emf.list(container, "portBindings");
            if (!ports.isEmpty()) {
                sb.append("    ports:\n");
                for (EObject pb : ports) {
                    sb.append("      - \"").append(Emf.integer(pb, "hostPort"))
                            .append(':').append(Emf.integer(pb, "containerPort")).append("\"\n");
                }
            }

            List<EObject> envs = Emf.list(container, "envBindings");
            if (!envs.isEmpty()) {
                sb.append("    environment:\n");
                for (EObject eb : envs) {
                    sb.append("      ").append(Emf.str(eb, "key"))
                            .append(": \"").append(Emf.str(eb, "value")).append("\"\n");
                }
            }

            List<EObject> dependsOn = Emf.list(container, "dependsOn");
            if (!dependsOn.isEmpty()) {
                sb.append("    depends_on:\n");
                for (EObject d : dependsOn) {
                    sb.append("      - ").append(Emf.str(d, "name")).append('\n');
                }
            }

            sb.append("    networks:\n      - m2ag-net\n");
        }

        sb.append("\nnetworks:\n  m2ag-net:\n    driver: bridge\n");

        Path out = generatedRoot.resolve("docker/docker-compose.yml");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        trace.add("docker/docker-compose.yml", "DeploymentTopology",
                "architecture2deployment.atl", "DockerCompose.mtl");
    }
}
