package mg.codel.m2ag.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import mg.codel.m2ag.pipeline.GenerationPipeline;
import mg.codel.m2ag.validation.Violation;

/**
 * Standalone HTTP front-end for the M2AG pipeline (the "unified web app"
 * backend). Built only on the JDK's {@code com.sun.net.httpserver} — no extra
 * dependency. Wraps {@link GenerationPipeline#execute} and serves the built
 * React UI from {@code visualization/dist}.
 *
 * <p>Run: {@code mvn exec:java -Dexec.mainClass=mg.codel.m2ag.web.WebServer}
 * then open {@code http://localhost:8080}. Single-user/demo server (one shared
 * {@code generated/} directory).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/models}            - list input model file names</li>
 *   <li>{@code GET  /api/model?name=X.xmi}  - raw XMI text of a model</li>
 *   <li>{@code POST /api/run?name=X.xmi}    - run the pipeline; returns
 *       validity, violations, deployment XMI and all generated artifacts</li>
 *   <li>{@code GET  /...}                   - static files from visualization/dist</li>
 * </ul>
 */
public final class WebServer {

    private final Path root;

    private WebServer(Path root) {
        this.root = root;
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new WebServer(root).start(port);
    }

    private void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/models", this::handleModels);
        server.createContext("/api/model", this::handleModel);
        server.createContext("/api/run", this::handleRun);
        server.createContext("/", this::handleStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("M2AG web server  ->  http://localhost:" + port + "   (Ctrl-C to stop)");
        System.out.println("project root     :  " + root);
        boolean built = Files.exists(root.resolve("visualization/dist/index.html"));
        System.out.println("frontend (dist)  :  " + (built ? "present" : "NOT built - run `cd visualization && npm run build`"));
    }

    // ---- API ---------------------------------------------------------------

    private void handleModels(HttpExchange ex) throws IOException {
        if (!preflight(ex)) {
            return;
        }
        List<String> names = new ArrayList<>();
        Path modelsDir = root.resolve("models");
        if (Files.isDirectory(modelsDir)) {
            try (Stream<Path> s = Files.list(modelsDir)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".xmi") && !n.endsWith("-deployment.xmi"))
                        .sorted()
                        .forEach(names::add);
            }
        }
        sendJson(ex, 200, names.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]")));
    }

    private void handleModel(HttpExchange ex) throws IOException {
        if (!preflight(ex)) {
            return;
        }
        Path model = safeModel(query(ex, "name"));
        if (model == null || !Files.exists(model)) {
            sendJson(ex, 404, "{\"error\":\"model not found\"}");
            return;
        }
        send(ex, 200, "application/xml; charset=utf-8", Files.readAllBytes(model));
    }

    /**
     * Streams the run live. The body is a sequence of newline-terminated lines,
     * each tagged by its first character: {@code 'L'} = a pipeline log line,
     * {@code 'R'} = the final JSON result. The client renders {@code L} lines in
     * a live console and parses the single {@code R} line as the result.
     */
    private void handleRun(HttpExchange ex) throws IOException {
        if (!preflight(ex)) {
            return;
        }
        Path model = safeModel(query(ex, "name"));
        if (model == null || !Files.exists(model)) {
            sendJson(ex, 400, "{\"error\":\"invalid or unknown model\"}");
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0); // 0 => chunked, unknown length
        OutputStream os = ex.getResponseBody();

        Consumer<String> log = line -> emit(os, 'L', line);
        try {
            GenerationPipeline.Outcome outcome = new GenerationPipeline().execute(root, model, log);
            emit(os, 'R', buildRunResponse(model.getFileName().toString(), outcome));
        } catch (Exception e) {
            emit(os, 'R', "{\"error\":" + Json.quote(String.valueOf(e.getMessage())) + "}");
        } finally {
            try {
                os.close();
            } catch (IOException ignored) {
                // client may have disconnected
            }
        }
    }

    /** Writes one tagged, newline-terminated line and flushes so it streams live. */
    private static void emit(OutputStream os, char tag, String payload) {
        try {
            os.write((tag + payload + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException ignored) {
            // client disconnected
        }
    }

    private String buildRunResponse(String name, GenerationPipeline.Outcome outcome)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":").append(Json.quote(name)).append(',');
        sb.append("\"valid\":").append(outcome.valid()).append(',');

        sb.append("\"violations\":[");
        List<Violation> vs = outcome.violations();
        for (int i = 0; i < vs.size(); i++) {
            Violation v = vs.get(i);
            sb.append("{\"rule\":").append(Json.quote(v.rule()))
                    .append(",\"elementId\":").append(Json.quote(v.elementId()))
                    .append(",\"message\":").append(Json.quote(v.message())).append('}');
            if (i < vs.size() - 1) {
                sb.append(',');
            }
        }
        sb.append("],");

        String deployment = null;
        if (outcome.deploymentModel() != null && Files.exists(outcome.deploymentModel())) {
            deployment = Files.readString(outcome.deploymentModel());
        }
        sb.append("\"deployment\":").append(deployment == null ? "null" : Json.quote(deployment)).append(',');

        sb.append("\"artifacts\":[");
        if (outcome.valid()) {
            Path generated = root.resolve("generated");
            List<Path> files;
            try (Stream<Path> walk = Files.walk(generated)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().equals("validation-report.json"))
                        .sorted()
                        .collect(Collectors.toList());
            }
            for (int i = 0; i < files.size(); i++) {
                Path p = files.get(i);
                String rel = generated.relativize(p).toString().replace('\\', '/');
                sb.append("{\"path\":").append(Json.quote(rel))
                        .append(",\"content\":").append(Json.quote(Files.readString(p))).append('}');
                if (i < files.size() - 1) {
                    sb.append(',');
                }
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---- static frontend ---------------------------------------------------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        Path dist = root.resolve("visualization/dist").normalize();
        Path file = dist.resolve(path.substring(1)).normalize();
        if (!file.startsWith(dist)) {
            sendText(ex, 403, "forbidden");
            return;
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            Path index = dist.resolve("index.html");
            if (Files.exists(index)) {
                file = index; // SPA fallback
            } else {
                sendText(ex, 200, notBuiltHtml());
                return;
            }
        }
        send(ex, 200, contentType(file), Files.readAllBytes(file));
    }

    private String notBuiltHtml() {
        return "<!doctype html><html><body style=\"font-family:sans-serif;max-width:640px;"
                + "margin:60px auto;line-height:1.5\"><h2>M²AG web server is running</h2>"
                + "<p>The API is live at <code>/api/...</code>, but the React UI has not been "
                + "built yet. Build it once:</p><pre>cd visualization &amp;&amp; npm install &amp;&amp; "
                + "npm run build</pre><p>then reload this page.</p>"
                + "<p>During development you can instead run <code>npm run dev</code> "
                + "(Vite proxies <code>/api</code> to this server).</p></body></html>";
    }

    // ---- helpers -----------------------------------------------------------

    private Path safeModel(String name) {
        if (name == null || name.contains("/") || name.contains("\\")
                || name.contains("..") || !name.endsWith(".xmi")) {
            return null;
        }
        return root.resolve("models").resolve(name);
    }

    private static boolean preflight(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return false;
        }
        return true;
    }

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return null;
        }
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key)) {
                return URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String contentType(Path file) {
        String n = file.getFileName().toString();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendText(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "text/html; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
