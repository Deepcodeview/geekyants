package com.geekyants.assessment.blueprint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Cross-field checks that bean validation cannot express. Applied to LLM output and admin edits alike. */
public final class BlueprintValidator {

    private static final Pattern CODE = Pattern.compile("REQ-\\d{3}");

    private BlueprintValidator() {
    }

    public static List<String> validate(BlueprintContent content) {
        List<String> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (BlueprintContent.Requirement r : content.requirements()) {
            if (!CODE.matcher(r.code()).matches()) {
                errors.add("Requirement code '%s' must look like REQ-001".formatted(r.code()));
            }
            if (!codes.add(r.code())) {
                errors.add("Duplicate requirement code " + r.code());
            }
        }
        for (BlueprintContent.ApiEndpoint api : content.apis()) {
            checkRefs(errors, codes, api.requirementCodes(), api.method() + " " + api.path());
        }
        for (BlueprintContent.MessagingEvent event : content.events()) {
            checkRefs(errors, codes, event.requirementCodes(), "event " + event.name());
        }
        return errors;
    }

    private static void checkRefs(List<String> errors, Set<String> codes, List<String> refs, String owner) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            if (!codes.contains(ref)) {
                errors.add("%s references unknown requirement %s".formatted(owner, ref));
            }
        }
    }
}
