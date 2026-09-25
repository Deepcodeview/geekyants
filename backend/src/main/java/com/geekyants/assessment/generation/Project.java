package com.geekyants.assessment.generation;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.blueprint.Blueprint;
import com.geekyants.assessment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** The generated project for an assignment, pinned to the blueprint version it was built from. */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id")
    private Blueprint blueprint;

    @Column(nullable = false)
    private String name;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    /** List of generated file paths, for traceability. */
    @Column(name = "manifest_json", nullable = false)
    private String manifestJson;

    protected Project() {
    }

    public Project(String publicId, Assignment assignment, Blueprint blueprint, String name, String templateKey,
                   String manifestJson) {
        this.publicId = publicId;
        this.assignment = assignment;
        this.blueprint = blueprint;
        this.name = name;
        this.templateKey = templateKey;
        this.manifestJson = manifestJson;
    }

    public String getPublicId() {
        return publicId;
    }

    public Blueprint getBlueprint() {
        return blueprint;
    }

    public String getName() {
        return name;
    }
}
