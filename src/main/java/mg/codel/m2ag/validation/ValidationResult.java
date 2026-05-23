package mg.codel.m2ag.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of running the OCL constraint set against an architecture model
 * (CDC section 6). A non-empty violation list halts the pipeline before any
 * transformation is triggered.
 */
public final class ValidationResult {

    private final List<Violation> violations = new ArrayList<>();

    public void add(Violation violation) {
        violations.add(violation);
    }

    public void add(String rule, String elementId, String message) {
        violations.add(new Violation(rule, elementId, message));
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public List<Violation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    @Override
    public String toString() {
        if (isValid()) {
            return "ValidationResult { valid=true }";
        }
        StringBuilder sb = new StringBuilder("ValidationResult { valid=false, violations=[\n");
        for (Violation v : violations) {
            sb.append("    ").append(v).append('\n');
        }
        return sb.append("] }").toString();
    }
}
