package mg.codel.m2ag.validation;

/**
 * A single OCL constraint violation (CDC section 6).
 *
 * @param rule      name of the violated OCL invariant, e.g. {@code UniquePort}
 * @param elementId xmi:id (or name) of the offending model element
 * @param message   human-readable explanation
 */
public record Violation(String rule, String elementId, String message) {

    @Override
    public String toString() {
        return "[" + rule + "] " + elementId + " : " + message;
    }
}
