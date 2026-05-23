package mg.codel.m2ag.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import mg.codel.m2ag.pipeline.Emf;

/**
 * Pipeline-integrated executor of the OCL constraint set (CDC sections 6 and 13).
 * <p>The declarative specification of these rules lives in
 * {@code metamodel/constraints.ocl} and is checked interactively in the Eclipse
 * OCL console. The methods below are their faithful executable counterparts:
 * each rule method names the OCL invariant it implements so the two stay in
 * lock-step. {@link #checkTransitiveCycles} is the DFS extension required by
 * CDC section 13 for cycles the per-element OCL invariants cannot catch.
 * <p>All checks run before any transformation; a non-empty result halts the
 * pipeline.
 */
public final class ValidationEngine {

    /** The {@code SystemArchitecture} root element of the M1 model. */
    private final EObject architecture;

    public ValidationEngine(EObject architecture) {
        this.architecture = architecture;
    }

    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        ruleUniquePort(result);          // Rule 1
        ruleUniqueServiceName(result);   // Rule 2
        ruleNoSelfDependency(result);    // Rule 3
        ruleValidPath(result);           // Rule 4
        ruleNoDuplicateRoutes(result);   // Rule 5
        ruleHasProvidedInterface(result);// Rule 6
        ruleUniqueDbPort(result);        // Rule 7
        ruleTargetHasPort(result);       // Rule 8
        checkTransitiveCycles(result);   // CDC section 13
        return result;
    }

    // ---- Rule 1 : UniquePort ------------------------------------------------
    private void ruleUniquePort(ValidationResult r) {
        List<EObject> svcs = services();
        for (EObject s : svcs) {
            int port = Emf.integer(s, "port");
            long count = svcs.stream().filter(o -> Emf.integer(o, "port") == port).count();
            if (count != 1) {
                r.add("UniquePort", Emf.elementId(s),
                        "Port " + port + " is bound by " + count
                                + " services; service ports must be unique.");
            }
        }
    }

    // ---- Rule 2 : UniqueServiceName ----------------------------------------
    private void ruleUniqueServiceName(ValidationResult r) {
        List<EObject> svcs = services();
        for (EObject s : svcs) {
            String name = Emf.str(s, "name");
            long count = svcs.stream()
                    .filter(o -> Objects.equals(Emf.str(o, "name"), name)).count();
            if (count != 1) {
                r.add("UniqueServiceName", Emf.elementId(s),
                        "Service name '" + name + "' is not unique (would overwrite "
                                + "the generated service directory).");
            }
        }
    }

    // ---- Rule 3 : NoSelfDependency -----------------------------------------
    private void ruleNoSelfDependency(ValidationResult r) {
        for (EObject s : services()) {
            for (EObject dep : Emf.list(s, "dependencies")) {
                EObject owner = owningService(Emf.ref(dep, "target"));
                if (owner == s) {
                    r.add("NoSelfDependency", Emf.elementId(s),
                            "Service '" + Emf.str(s, "name") + "' depends on itself.");
                }
            }
        }
    }

    // ---- Rule 4 : ValidPath ------------------------------------------------
    private void ruleValidPath(ValidationResult r) {
        for (EObject e : allOfType("APIEndpoint")) {
            String path = Emf.str(e, "path");
            if (path == null || !path.startsWith("/")) {
                r.add("ValidPath", Emf.elementId(e),
                        "Endpoint path '" + path + "' must start with '/'.");
            }
        }
    }

    // ---- Rule 5 : NoDuplicateRoutes ----------------------------------------
    private void ruleNoDuplicateRoutes(ValidationResult r) {
        for (EObject iface : allOfType("ServiceInterface")) {
            Set<String> seen = new HashSet<>();
            for (EObject e : Emf.list(iface, "endpoints")) {
                String route = Emf.str(e, "path") + " " + Emf.enumLiteral(e, "method");
                if (!seen.add(route)) {
                    r.add("NoDuplicateRoutes", Emf.elementId(iface),
                            "Interface '" + Emf.str(iface, "name")
                                    + "' declares duplicate route " + route + ".");
                }
            }
        }
    }

    // ---- Rule 6 : HasProvidedInterface ------------------------------------
    private void ruleHasProvidedInterface(ValidationResult r) {
        for (EObject s : services()) {
            if (Emf.list(s, "providedInterfaces").isEmpty()) {
                r.add("HasProvidedInterface", Emf.elementId(s),
                        "Service '" + Emf.str(s, "name")
                                + "' must provide at least one interface.");
            }
        }
    }

    // ---- Rule 7 : UniqueDBPort --------------------------------------------
    private void ruleUniqueDbPort(ValidationResult r) {
        List<EObject> dbs = Emf.list(architecture, "databases");
        for (EObject db : dbs) {
            int port = Emf.integer(db, "port");
            long count = dbs.stream().filter(o -> Emf.integer(o, "port") == port).count();
            if (count != 1) {
                r.add("UniqueDBPort", Emf.elementId(db),
                        "Database port " + port + " is bound by " + count
                                + " databases; database ports must be unique.");
            }
        }
    }

    // ---- Rule 8 : TargetHasPort -------------------------------------------
    private void ruleTargetHasPort(ValidationResult r) {
        for (EObject dep : allOfType("ServiceDependency")) {
            EObject owner = owningService(Emf.ref(dep, "target"));
            if (owner == null || Emf.integer(owner, "port") <= 0) {
                r.add("TargetHasPort", Emf.elementId(dep),
                        "Dependency target interface is not exposed by a service "
                                + "with a valid port.");
            }
        }
    }

    // ---- CDC section 13 : transitive circular dependency (DFS) -------------
    private void checkTransitiveCycles(ValidationResult r) {
        Map<EObject, List<EObject>> graph = new HashMap<>();
        for (EObject s : services()) {
            List<EObject> edges = new ArrayList<>();
            for (EObject dep : Emf.list(s, "dependencies")) {
                EObject owner = owningService(Emf.ref(dep, "target"));
                if (owner != null) {
                    edges.add(owner);
                }
            }
            graph.put(s, edges);
        }
        Set<EObject> visited = new HashSet<>();
        Set<EObject> reported = new HashSet<>();
        for (EObject s : services()) {
            if (!visited.contains(s)) {
                dfs(s, graph, visited, new HashSet<>(), new ArrayDeque<>(), reported, r);
            }
        }
    }

    private void dfs(EObject node, Map<EObject, List<EObject>> graph,
            Set<EObject> visited, Set<EObject> inStack, Deque<EObject> path,
            Set<EObject> reported, ValidationResult r) {
        visited.add(node);
        inStack.add(node);
        path.addLast(node);
        for (EObject next : graph.getOrDefault(node, List.of())) {
            if (inStack.contains(next) && reported.add(next)) {
                StringBuilder cycle = new StringBuilder();
                boolean inCycle = false;
                for (EObject n : path) {
                    inCycle |= n == next;
                    if (inCycle) {
                        cycle.append(Emf.str(n, "name")).append(" -> ");
                    }
                }
                cycle.append(Emf.str(next, "name"));
                r.add("NoTransitiveCycle", Emf.elementId(next),
                        "Circular service dependency: " + cycle);
            } else if (!visited.contains(next)) {
                dfs(next, graph, visited, inStack, path, reported, r);
            }
        }
        inStack.remove(node);
        path.removeLast();
    }

    // ---- model navigation helpers -----------------------------------------
    private List<EObject> services() {
        return Emf.list(architecture, "services");
    }

    private EObject owningService(EObject iface) {
        if (iface == null) {
            return null;
        }
        for (EObject s : services()) {
            if (Emf.list(s, "providedInterfaces").contains(iface)) {
                return s;
            }
        }
        return null;
    }

    private List<EObject> allOfType(String typeName) {
        List<EObject> out = new ArrayList<>();
        for (var it = architecture.eAllContents(); it.hasNext();) {
            EObject e = it.next();
            if (Emf.isType(e, typeName)) {
                out.add(e);
            }
        }
        return out;
    }
}
