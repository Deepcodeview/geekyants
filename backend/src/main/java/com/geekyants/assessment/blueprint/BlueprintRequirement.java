package com.geekyants.assessment.blueprint;

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
import jakarta.persistence.Table;

/** Requirements are stored as rows too, so reports and MCQs can join on REQ codes. */
@Entity
@Table(name = "blueprint_requirements")
public class BlueprintRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id")
    private Blueprint blueprint;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlueprintContent.Category category;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(name = "acceptance_criteria_json", nullable = false)
    private String acceptanceCriteriaJson;

    protected BlueprintRequirement() {
    }

    BlueprintRequirement(Blueprint blueprint, BlueprintContent.Requirement requirement, String criteriaJson) {
        this.blueprint = blueprint;
        this.code = requirement.code();
        this.title = requirement.title();
        this.description = requirement.description();
        this.category = requirement.category();
        this.mandatory = requirement.mandatory();
        this.acceptanceCriteriaJson = criteriaJson;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
