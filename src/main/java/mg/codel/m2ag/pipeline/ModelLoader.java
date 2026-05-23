package mg.codel.m2ag.pipeline;

import java.nio.file.Path;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Standalone EMF bootstrap (no Eclipse runtime). Registers the XMI/Ecore
 * resource factories, loads the {@code .ecore} metamodels dynamically and
 * loads {@code .xmi} model instances against them.
 */
public final class ModelLoader {

    private final ResourceSet resourceSet;

    public ModelLoader() {
        resourceSet = new ResourceSetImpl();
        Resource.Factory.Registry registry = resourceSet.getResourceFactoryRegistry();
        registry.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        registry.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        registry.getExtensionToFactoryMap().put(
                Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
    }

    /** Loads an {@code .ecore} metamodel and registers it by its nsURI. */
    public EPackage registerMetamodel(Path ecoreFile) {
        Resource resource = resourceSet.getResource(uri(ecoreFile), true);
        EPackage pkg = (EPackage) resource.getContents().get(0);
        resourceSet.getPackageRegistry().put(pkg.getNsURI(), pkg);
        return pkg;
    }

    /** Loads an {@code .xmi} model instance; its metamodel must already be registered. */
    public EObject loadModel(Path xmiFile) {
        Resource resource = resourceSet.getResource(uri(xmiFile), true);
        if (resource.getContents().isEmpty()) {
            throw new IllegalStateException("Model resource is empty: " + xmiFile);
        }
        return resource.getContents().get(0);
    }

    /** Creates a new resource (used to persist the derived deployment model). */
    public Resource createResource(Path file) {
        return resourceSet.createResource(uri(file));
    }

    public ResourceSet resourceSet() {
        return resourceSet;
    }

    private static URI uri(Path path) {
        return URI.createFileURI(path.toAbsolutePath().toString());
    }
}
