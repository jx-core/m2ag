package mg.codel.m2ag.pipeline;

import java.util.List;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * Thin reflective accessor over dynamic EMF instances.
 * <p>The pipeline loads {@code .ecore} metamodels at runtime (dynamic EMF), so
 * there is no generated model API; every model read goes through these helpers.
 */
public final class Emf {

    private Emf() {
    }

    public static EStructuralFeature feature(EObject o, String name) {
        EStructuralFeature f = o.eClass().getEStructuralFeature(name);
        if (f == null) {
            throw new IllegalArgumentException(
                    "No feature '" + name + "' on " + o.eClass().getName());
        }
        return f;
    }

    public static Object get(EObject o, String name) {
        return o.eGet(feature(o, name));
    }

    public static String str(EObject o, String name) {
        Object v = get(o, name);
        return v == null ? null : v.toString();
    }

    public static int integer(EObject o, String name) {
        Object v = get(o, name);
        return v == null ? 0 : ((Number) v).intValue();
    }

    public static boolean bool(EObject o, String name) {
        Object v = get(o, name);
        return v != null && (Boolean) v;
    }

    @SuppressWarnings("unchecked")
    public static List<EObject> list(EObject o, String name) {
        Object v = get(o, name);
        return v == null ? List.of() : (List<EObject>) v;
    }

    public static EObject ref(EObject o, String name) {
        return (EObject) get(o, name);
    }

    /** Literal name of an EEnum-typed attribute, e.g. {@code POSTGRESQL}. */
    public static String enumLiteral(EObject o, String name) {
        Object v = get(o, name);
        if (v == null) {
            return null;
        }
        return v instanceof Enumerator e ? e.getName() : v.toString();
    }

    public static String typeName(EObject o) {
        return o.eClass().getName();
    }

    public static boolean isType(EObject o, String typeName) {
        return o.eClass().getName().equals(typeName);
    }

    public static void set(EObject o, String name, Object value) {
        o.eSet(feature(o, name), value);
    }

    /** Stable identity of an element: its {@code xmi:id}, else its name, else its type. */
    public static String elementId(EObject o) {
        if (o.eResource() instanceof XMLResource xml) {
            String id = xml.getID(o);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        EStructuralFeature nameFeature = o.eClass().getEStructuralFeature("name");
        if (nameFeature != null && o.eGet(nameFeature) != null) {
            return o.eGet(nameFeature).toString();
        }
        return o.eClass().getName();
    }
}
