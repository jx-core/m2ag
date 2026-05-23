package mg.codel.m2ag.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.emf.ecore.EObject;

/**
 * M2T generation stage : architecture model &rarr; one Spring Boot + Maven
 * service skeleton per microservice.
 * <p>Headless executable mirror of
 * {@code generators/acceleo/SpringBootService.mtl} (CDC section 8.1). Every
 * generated file carries the {@code [M2AG-TRACE]} header required by CDC
 * section 9.
 */
public final class SpringBootGenerator {

    public void generate(EObject architecture, Path generatedRoot, TraceabilityWriter trace)
            throws IOException {
        String system = Emf.str(architecture, "name");
        for (EObject service : Emf.list(architecture, "services")) {
            String name = Emf.str(service, "name");
            String pkgPath = "mg/codel/m2ag/" + name.toLowerCase();
            String javaRoot = "services/" + name + "/src/main/java/" + pkgPath;
            String source = "Microservice(" + name + ")";

            emit(generatedRoot, "services/" + name + "/pom.xml", pom(service), trace, source);
            emit(generatedRoot, javaRoot + "/" + name + "Application.java",
                    application(service, system), trace, source);
            emit(generatedRoot, javaRoot + "/controller/" + name + "Controller.java",
                    controller(service, system), trace, source);
            emit(generatedRoot, javaRoot + "/service/" + name + "Service.java",
                    serviceClass(service, system), trace, source);
            emit(generatedRoot, "services/" + name + "/src/main/resources/application.properties",
                    properties(service), trace, source);
            emit(generatedRoot, "services/" + name + "/Dockerfile",
                    dockerfile(service), trace, source);
        }
    }

    private void emit(Path root, String relative, String content,
            TraceabilityWriter trace, String source) throws IOException {
        Path out = root.resolve(relative);
        Files.createDirectories(out.getParent());
        Files.writeString(out, content, StandardCharsets.UTF_8);
        trace.add(relative, source, "-", "SpringBootService.mtl");
    }

    // ---- file bodies -------------------------------------------------------

    private static String javaHeader(String name, String system) {
        return """
                // ============================================================
                // [M2AG-TRACE] Source element : Microservice(%s)
                // [M2AG-TRACE] Template       : SpringBootService.mtl
                // [M2AG-TRACE] System         : %s
                // DO NOT EDIT - regenerate from model
                // ============================================================
                """.formatted(name, system);
    }

    private static String pkg(String name) {
        return "mg.codel.m2ag." + name.toLowerCase();
    }

    private String pom(EObject service) {
        String name = Emf.str(service, "name");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- [M2AG-TRACE] Source: Microservice(%s) | Template: SpringBootService.mtl -->
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>

                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.4</version>
                    <relativePath/>
                  </parent>

                  <groupId>mg.codel.m2ag</groupId>
                  <artifactId>%s-service</artifactId>
                  <version>1.0.0</version>
                  <name>%s</name>

                  <properties>
                    <java.version>17</java.version>
                  </properties>

                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>

                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """.formatted(name, name.toLowerCase(), name);
    }

    private String application(EObject service, String system) {
        String name = Emf.str(service, "name");
        return javaHeader(name, system) + """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class %sApplication {

                    public static void main(String[] args) {
                        SpringApplication.run(%sApplication.class, args);
                    }
                }
                """.formatted(pkg(name), name, name);
    }

    private String controller(EObject service, String system) {
        String name = Emf.str(service, "name");
        StringBuilder sb = new StringBuilder(javaHeader(name, system));
        sb.append("package ").append(pkg(name)).append(".controller;\n\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n\n");
        sb.append("@RestController\n");
        sb.append("public class ").append(name).append("Controller {\n");
        for (EObject iface : Emf.list(service, "providedInterfaces")) {
            for (EObject endpoint : Emf.list(iface, "endpoints")) {
                String method = Emf.enumLiteral(endpoint, "method");
                String path = Emf.str(endpoint, "path");
                sb.append("\n    // [M2AG-TRACE] APIEndpoint: ").append(method).append(' ')
                        .append(path).append(" (authRequired=")
                        .append(Emf.bool(endpoint, "authRequired")).append(")\n");
                sb.append("    @").append(cap(method.toLowerCase())).append("Mapping(\"")
                        .append(path).append("\")\n");
                sb.append("    public ResponseEntity<?> ").append(opName(endpoint)).append("() {\n");
                sb.append("        // TODO: implement - generated stub\n");
                sb.append("        return ResponseEntity.ok().build();\n");
                sb.append("    }\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String serviceClass(EObject service, String system) {
        String name = Emf.str(service, "name");
        return javaHeader(name, system) + """
                package %s.service;

                import org.springframework.stereotype.Service;

                @Service
                public class %sService {

                    // Business-logic layer for Microservice(%s).
                    // TODO: implement - generated stub
                }
                """.formatted(pkg(name), name, name);
    }

    private String properties(EObject service) {
        String name = Emf.str(service, "name");
        return "# [M2AG-TRACE] Source: Microservice(" + name
                + ") | Template: SpringBootService.mtl\n"
                + "server.port=" + Emf.integer(service, "port") + "\n"
                + "spring.application.name=" + name + "\n";
    }

    private String dockerfile(EObject service) {
        String name = Emf.str(service, "name");
        return "# [M2AG-TRACE] Source: Microservice(" + name
                + ") | Template: SpringBootService.mtl\n"
                + "FROM eclipse-temurin:17-jre\n"
                + "WORKDIR /app\n"
                + "COPY target/" + name.toLowerCase() + "-service-1.0.0.jar app.jar\n"
                + "EXPOSE " + Emf.integer(service, "port") + "\n"
                + "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";
    }

    // ---- naming helpers ----------------------------------------------------

    /** Java handler name, e.g. {@code GET /products/{id}} -> {@code getProductsId}. */
    private static String opName(EObject endpoint) {
        StringBuilder sb = new StringBuilder(Emf.enumLiteral(endpoint, "method").toLowerCase());
        for (String token : Emf.str(endpoint, "path").split("[/{}]")) {
            if (!token.isEmpty()) {
                sb.append(cap(token));
            }
        }
        return sb.toString();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
