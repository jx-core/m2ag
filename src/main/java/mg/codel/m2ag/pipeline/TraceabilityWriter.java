package mg.codel.m2ag.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects an artifact-to-model traceability record during generation and
 * writes {@code generated/traceability.json} (CDC section 9.3). The file
 * demonstrates that every generated artifact is model-driven and reproducible.
 */
public final class TraceabilityWriter {

    /**
     * One generated artifact and its provenance.
     *
     * @param file           path of the generated artifact, relative to {@code generated/}
     * @param sourceElement  the M1 model element it derives from
     * @param transformation the ATL transformation in its lineage, or {@code "-"}
     * @param template       the Acceleo template that produced it
     */
    public record Entry(String file, String sourceElement, String transformation,
                        String template) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(Entry entry) {
        entries.add(entry);
    }

    public void add(String file, String sourceElement, String transformation, String template) {
        entries.add(new Entry(file, sourceElement, transformation, template));
    }

    public int size() {
        return entries.size();
    }

    public void write(Path file, String modelPath) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"model\": \"").append(escape(modelPath)).append("\",\n");
        sb.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"artifacts\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            sb.append("    {\n");
            sb.append("      \"file\": \"").append(escape(e.file())).append("\",\n");
            sb.append("      \"sourceElement\": \"").append(escape(e.sourceElement())).append("\",\n");
            sb.append("      \"transformation\": \"").append(escape(e.transformation())).append("\",\n");
            sb.append("      \"template\": \"").append(escape(e.template())).append("\"\n");
            sb.append("    }").append(i < entries.size() - 1 ? "," : "").append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
