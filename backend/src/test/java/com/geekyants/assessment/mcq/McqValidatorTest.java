package com.geekyants.assessment.mcq;

import static org.assertj.core.api.Assertions.assertThat;

import com.geekyants.assessment.Fixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class McqValidatorTest {

    @Test
    void acceptsTwelveToFifteen() {
        assertThat(McqValidator.validate(Fixtures.mcqs(12), 12, 15)).isEmpty();
        assertThat(McqValidator.validate(Fixtures.mcqs(15), 12, 15)).isEmpty();
    }

    @Test
    void rejectsWrongCount() {
        assertThat(McqValidator.validate(Fixtures.mcqs(11), 12, 15)).anyMatch(e -> e.contains("got 11"));
        assertThat(McqValidator.validate(Fixtures.mcqs(16), 12, 15)).anyMatch(e -> e.contains("got 16"));
    }

    @Test
    void rejectsBadOptionsAndGenericQuestionSets() {
        List<GeneratedMcqs.Question> qs = new ArrayList<>();
        for (GeneratedMcqs.Question q : Fixtures.mcqs(12).questions()) {
            qs.add(new GeneratedMcqs.Question(q.category(), q.stem(), List.of("same", "same", "x", "y"), 7,
                    q.explanation(), null, null));
        }
        List<String> errors = McqValidator.validate(new GeneratedMcqs(qs), 12, 15);
        assertThat(errors).anyMatch(e -> e.contains("duplicate options"))
                .anyMatch(e -> e.contains("out-of-range"))
                .anyMatch(e -> e.contains("reference the candidate's code"));
    }
}
