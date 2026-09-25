package com.geekyants.assessment.blueprint;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.common.BaseEntity;
import com.geekyants.assessment.common.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One immutable version of a project blueprint. Edits create a new version. */
@Entity
@Table(name = "blueprints")
public class Blueprint extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlueprintStatus status;

    @Column(name = "content_json", nullable = false)
    private String contentJson;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @OneToMany(mappedBy = "blueprint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("code")
    private List<BlueprintRequirement> requirements = new ArrayList<>();

    protected Blueprint() {
    }

    public Blueprint(String publicId, Assignment assignment, int versionNo, String contentJson,
                     List<BlueprintContent.Requirement> requirements, List<String> criteriaJson) {
        this.publicId = publicId;
        this.assignment = assignment;
        this.versionNo = versionNo;
        this.contentJson = contentJson;
        this.status = BlueprintStatus.DRAFT;
        for (int i = 0; i < requirements.size(); i++) {
            this.requirements.add(new BlueprintRequirement(this, requirements.get(i), criteriaJson.get(i)));
        }
    }

    public void approve(String actor, Instant now) {
        if (status != BlueprintStatus.DRAFT) {
            throw new ConflictException("Blueprint %s is %s and cannot be approved".formatted(publicId, status));
        }
        status = BlueprintStatus.APPROVED;
        approvedBy = actor;
        approvedAt = now;
    }

    public void supersede() {
        if (status == BlueprintStatus.APPROVED) {
            throw new ConflictException("Approved blueprint %s cannot be superseded".formatted(publicId));
        }
        status = BlueprintStatus.SUPERSEDED;
    }

    public String getPublicId() {
        return publicId;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public BlueprintStatus getStatus() {
        return status;
    }

    public String getContentJson() {
        return contentJson;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public List<BlueprintRequirement> getRequirements() {
        return requirements;
    }
}
