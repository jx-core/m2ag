package mg.codel.m2ag.pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

/**
 * M2M transformation stage : architecture model &rarr; deployment topology.
 * <p>The authoritative model-driven definition of this transformation is
 * {@code transformations/architecture2deployment.atl}, run in Classic Eclipse
 * (CDC phase 5). This class is its headless executable mirror: each step
 * corresponds one-to-one to an ATL rule of the same name, so the pipeline can
 * run end-to-end without an Eclipse/ATL install. The deployment metamodel and
 * the DBType&rarr;image mapping follow CDC section 5.
 */
public final class DeploymentTransformer {

    private final EPackage deploymentMetamodel;

    public DeploymentTransformer(EPackage deploymentMetamodel) {
        this.deploymentMetamodel = deploymentMetamodel;
    }

    /** Mirrors ATL {@code create OUT : Deployment from IN : MicroserviceArchitecture}. */
    public EObject transform(EObject architecture) {
        EObject topology = create("DeploymentTopology");
        List<EObject> containers = Emf.list(topology, "containers");
        List<EObject> networks = Emf.list(topology, "networks");

        Map<EObject, EObject> serviceToContainer = new HashMap<>();

        // Rule MicroserviceToContainer.
        for (EObject service : Emf.list(architecture, "services")) {
            EObject container = create("Container");
            Emf.set(container, "name", Emf.str(service, "name"));
            Emf.set(container, "buildContext", "../services/" + Emf.str(service, "name"));
            int port = Emf.integer(service, "port");
            if (port > 0) {
                Emf.list(container, "portBindings").add(portBinding(port));
            }
            for (EObject envVar : Emf.list(service, "environmentVariables")) {
                Emf.list(container, "envBindings").add(envBinding(envVar));
            }
            serviceToContainer.put(service, container);
            containers.add(container);
        }

        // Rule DatabaseToContainer.
        for (EObject database : Emf.list(architecture, "databases")) {
            EObject container = create("Container");
            String type = Emf.enumLiteral(database, "type");
            Emf.set(container, "name", type.toLowerCase() + "-" + Emf.integer(database, "port"));
            Emf.set(container, "image", resolveImage(type));
            Emf.list(container, "portBindings").add(portBinding(Emf.integer(database, "port")));
            containers.add(container);
        }

        // Rule ServiceDependencyToNetworkLink + dependsOn population.
        for (EObject service : Emf.list(architecture, "services")) {
            EObject sourceContainer = serviceToContainer.get(service);
            for (EObject dependency : Emf.list(service, "dependencies")) {
                EObject targetService = owningService(architecture, Emf.ref(dependency, "target"));
                EObject targetContainer = serviceToContainer.get(targetService);
                if (targetContainer == null) {
                    continue;
                }
                Emf.list(sourceContainer, "dependsOn").add(targetContainer);
                EObject link = create("NetworkLink");
                Emf.set(link, "source", sourceContainer);
                Emf.set(link, "target", targetContainer);
                networks.add(link);
            }
        }
        return topology;
    }

    /** ATL helper {@code resolveImage(DBType)} (CDC section 5.2). */
    private static String resolveImage(String dbType) {
        return switch (dbType) {
            case "POSTGRESQL" -> "postgres:15-alpine";
            case "MYSQL" -> "mysql:8.0";
            case "MONGODB" -> "mongo:6.0";
            case "REDIS" -> "redis:7-alpine";
            default -> "unknown";
        };
    }

    /** ATL helper {@code ServiceInterface.owningService}. */
    private static EObject owningService(EObject architecture, EObject iface) {
        if (iface == null) {
            return null;
        }
        for (EObject service : Emf.list(architecture, "services")) {
            if (Emf.list(service, "providedInterfaces").contains(iface)) {
                return service;
            }
        }
        return null;
    }

    /** ATL called rule {@code makePortBinding(Integer)}. */
    private EObject portBinding(int port) {
        EObject binding = create("PortBinding");
        Emf.set(binding, "hostPort", port);
        Emf.set(binding, "containerPort", port);
        return binding;
    }

    /** ATL rule {@code EnvironmentVariableToBinding}. */
    private EObject envBinding(EObject envVar) {
        EObject binding = create("EnvironmentBinding");
        Emf.set(binding, "key", Emf.str(envVar, "key"));
        Emf.set(binding, "value", Emf.str(envVar, "value"));
        return binding;
    }

    private EObject create(String className) {
        EClass eClass = (EClass) deploymentMetamodel.getEClassifier(className);
        if (eClass == null) {
            throw new IllegalStateException("Deployment.ecore has no class " + className);
        }
        return deploymentMetamodel.getEFactoryInstance().create(eClass);
    }
}
