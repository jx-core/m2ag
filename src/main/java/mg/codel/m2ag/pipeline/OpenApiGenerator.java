package mg.codel.m2ag.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

/**
 * M2T generation stage : architecture model &rarr; one OpenAPI 3.0 spec per
 * microservice.
 * <p>Headless executable mirror of {@code generators/acceleo/OpenAPI.mtl}
 * (CDC section 8.3). API contracts are a logical concern, so this reads the
 * architecture model directly rather than the deployment model.
 */
public final class OpenApiGenerator {

    public void generate(EObject architecture, Path generatedRoot, TraceabilityWriter trace)
            throws IOException {
        String system = Emf.str(architecture, "name");
        for (EObject service : Emf.list(architecture, "services")) {
            String name = Emf.str(service, "name");

            List<EObject> endpoints = new ArrayList<>();
            for (EObject iface : Emf.list(service, "providedInterfaces")) {
                endpoints.addAll(Emf.list(iface, "endpoints"));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# ============================================================\n");
            sb.append("# [M2AG-TRACE] Source element : Microservice(").append(name).append(")\n");
            sb.append("# [M2AG-TRACE] Template       : OpenAPI.mtl\n");
            sb.append("# [M2AG-TRACE] System         : ").append(system).append('\n');
            sb.append("# DO NOT EDIT - regenerate from model\n");
            sb.append("# ============================================================\n");
            sb.append("openapi: 3.0.3\n");
            sb.append("info:\n");
            sb.append("  title: ").append(name).append(" API\n");
            sb.append("  version: 1.0.0\n");
            sb.append("  description: Generated from ").append(system)
                    .append(" by the M2AG pipeline.\n");
            sb.append("servers:\n");
            sb.append("  - url: http://localhost:").append(Emf.integer(service, "port")).append('\n');
            sb.append("paths:\n");

            List<String> paths = endpoints.stream()
                    .map(e -> Emf.str(e, "path")).distinct().sorted().toList();
            for (String path : paths) {
                sb.append("  ").append(path).append(":\n");
                for (EObject e : endpoints) {
                    if (!path.equals(Emf.str(e, "path"))) {
                        continue;
                    }
                    String method = Emf.enumLiteral(e, "method");
                    String verb = method.toLowerCase();
                    sb.append("    ").append(verb).append(":\n");
                    sb.append("      summary: ").append(method).append(' ').append(path).append('\n');
                    sb.append("      operationId: ").append(verb).append(path).append('\n');
                    if (path.contains("{")) {
                        String param = path.substring(path.indexOf('{') + 1, path.indexOf('}'));
                        sb.append("      parameters:\n");
                        sb.append("        - name: ").append(param).append('\n');
                        sb.append("          in: path\n");
                        sb.append("          required: true\n");
                        sb.append("          schema:\n");
                        sb.append("            type: string\n");
                    }
                    if (Emf.bool(e, "authRequired")) {
                        sb.append("      security:\n");
                        sb.append("        - bearerAuth: []\n");
                    }
                    sb.append("      responses:\n");
                    sb.append("        '200':\n");
                    sb.append("          description: Successful response\n");
                }
            }

            sb.append("components:\n");
            sb.append("  securitySchemes:\n");
            sb.append("    bearerAuth:\n");
            sb.append("      type: http\n");
            sb.append("      scheme: bearer\n");
            sb.append("      bearerFormat: JWT\n");

            String relative = "openapi/" + name + "-openapi.yaml";
            Path out = generatedRoot.resolve(relative);
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
            trace.add(relative, "Microservice(" + name + ")", "-", "OpenAPI.mtl");
        }
    }
}
