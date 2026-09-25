package com.geekyants.assessment.organization;

import com.geekyants.assessment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String industry;

    private String domain;

    @Column(name = "standards_json")
    private String standardsJson;

    protected Company() {
    }

    public Company(String externalId) {
        this.externalId = externalId;
    }

    public void update(String name, String industry, String domain, String standardsJson) {
        this.name = name;
        this.industry = industry;
        this.domain = domain;
        this.standardsJson = standardsJson;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getIndustry() {
        return industry;
    }

    public String getDomain() {
        return domain;
    }

    public String getStandardsJson() {
        return standardsJson;
    }
}
