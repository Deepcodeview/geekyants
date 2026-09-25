package com.geekyants.assessment.mcq;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mcq_questions")
public class McqQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id")
    private McqAssessment assessment;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private McqCategory category;

    @Column(nullable = false)
    private String stem;

    @Column(name = "options_json", nullable = false)
    private String optionsJson;

    /** Never sent to the candidate. */
    @Column(name = "correct_option", nullable = false)
    private int correctOption;

    private String explanation;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "requirement_code")
    private String requirementCode;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private McqAnswer answer;

    protected McqQuestion() {
    }

    public McqQuestion(McqAssessment assessment, String code, int position, McqCategory category, String stem,
                       String optionsJson, int correctOption, String explanation, String sourceRef,
                       String requirementCode) {
        this.assessment = assessment;
        this.code = code;
        this.position = position;
        this.category = category;
        this.stem = stem;
        this.optionsJson = optionsJson;
        this.correctOption = correctOption;
        this.explanation = explanation;
        this.sourceRef = sourceRef;
        this.requirementCode = requirementCode;
    }

    public void answer(int selectedOption, Instant now) {
        if (answer == null) {
            answer = new McqAnswer(this, selectedOption, selectedOption == correctOption, now);
        } else {
            answer.change(selectedOption, selectedOption == correctOption, now);
        }
    }

    public String getCode() {
        return code;
    }

    public int getPosition() {
        return position;
    }

    public McqCategory getCategory() {
        return category;
    }

    public String getStem() {
        return stem;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public int getCorrectOption() {
        return correctOption;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public McqAnswer getAnswer() {
        return answer;
    }
}
