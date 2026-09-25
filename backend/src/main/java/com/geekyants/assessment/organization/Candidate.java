package com.geekyants.assessment.organization;

import com.geekyants.assessment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidates")
public class Candidate extends BaseEntity {

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String email;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "profile_json", nullable = false)
    private String profileJson;

    protected Candidate() {
    }

    public Candidate(String externalId) {
        this.externalId = externalId;
    }

    public void update(String fullName, String email, String profileJson) {
        this.fullName = fullName;
        this.email = email;
        this.profileJson = profileJson;
    }

    public void linkGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public String getProfileJson() {
        return profileJson;
    }
}
