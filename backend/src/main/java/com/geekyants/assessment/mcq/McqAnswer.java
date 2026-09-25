package com.geekyants.assessment.mcq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mcq_answers")
public class McqAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private McqQuestion question;

    @Column(name = "selected_option", nullable = false)
    private int selectedOption;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    protected McqAnswer() {
    }

    McqAnswer(McqQuestion question, int selectedOption, boolean correct, Instant answeredAt) {
        this.question = question;
        this.selectedOption = selectedOption;
        this.correct = correct;
        this.answeredAt = answeredAt;
    }

    void change(int selectedOption, boolean correct, Instant answeredAt) {
        this.selectedOption = selectedOption;
        this.correct = correct;
        this.answeredAt = answeredAt;
    }

    public int getSelectedOption() {
        return selectedOption;
    }

    public boolean isCorrect() {
        return correct;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
