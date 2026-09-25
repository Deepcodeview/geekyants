package com.geekyants.assessment.organization;

import com.geekyants.assessment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "jobs")
public class Job extends BaseEntity {

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false)
    private String title;

    private String department;

    private String seniority;

    @Column(name = "required_experience")
    private String requiredExperience;

    @Column(name = "description_json", nullable = false)
    private String descriptionJson;

    protected Job() {
    }

    public Job(Company company, String externalId) {
        this.company = company;
        this.externalId = externalId;
    }

    public void update(String title, String department, String seniority, String requiredExperience,
                       String descriptionJson) {
        this.title = title;
        this.department = department;
        this.seniority = seniority;
        this.requiredExperience = requiredExperience;
        this.descriptionJson = descriptionJson;
    }

    public String getExternalId() {
        return externalId;
    }

    public Company getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getSeniority() {
        return seniority;
    }

    public String getDescriptionJson() {
        return descriptionJson;
    }
}
