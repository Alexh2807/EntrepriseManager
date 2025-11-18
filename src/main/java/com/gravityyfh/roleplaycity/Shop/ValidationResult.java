package com.gravityyfh.roleplaycity.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Résultat d'une validation de boutique
 */
public class ValidationResult {
    private final boolean valid;
    private final List<String> issues;
    private final RepairAction suggestedAction;

    public ValidationResult(boolean valid, List<String> issues, RepairAction suggestedAction) {
        this.valid = valid;
        this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
        this.suggestedAction = suggestedAction != null ? suggestedAction : RepairAction.NONE;
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, Collections.emptyList(), RepairAction.NONE);
    }

    public static ValidationResult broken(String issue, RepairAction action) {
        List<String> issues = new ArrayList<>();
        issues.add(issue);
        return new ValidationResult(false, issues, action);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public RepairAction getSuggestedAction() {
        return suggestedAction;
    }

    public String getIssuesAsString() {
        return String.join(", ", issues);
    }
}
