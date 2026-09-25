-- Core schema for the assessment platform.
-- JSON documents are stored as MEDIUMTEXT (validated in the application) so the
-- schema stays portable to the H2 database used in tests.

CREATE TABLE companies (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id     VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    industry        VARCHAR(128),
    domain          VARCHAR(128),
    standards_json  MEDIUMTEXT,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_companies_external_id UNIQUE (external_id)
);

CREATE TABLE jobs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id         VARCHAR(64)  NOT NULL,
    company_id          BIGINT       NOT NULL,
    title               VARCHAR(255) NOT NULL,
    department          VARCHAR(128),
    seniority           VARCHAR(64),
    required_experience VARCHAR(64),
    description_json    MEDIUMTEXT   NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uk_jobs_external_id UNIQUE (company_id, external_id),
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE TABLE candidates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id         VARCHAR(64)  NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    email               VARCHAR(255),
    github_username     VARCHAR(39),
    profile_json        MEDIUMTEXT   NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uk_candidates_external_id UNIQUE (external_id)
);

CREATE TABLE assignments (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id                   VARCHAR(40)  NOT NULL,
    company_id                  BIGINT       NOT NULL,
    job_id                      BIGINT       NOT NULL,
    candidate_id                BIGINT       NOT NULL,
    status                      VARCHAR(32)  NOT NULL,
    requirement_analysis_json   MEDIUMTEXT,
    project_config_json         MEDIUMTEXT,
    duration_days               INT,
    assigned_at                 DATETIME(6),
    due_at                      DATETIME(6),
    failure_reason              VARCHAR(2000),
    failed_from_status          VARCHAR(32),
    row_version                 BIGINT       NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_assignments_public_id UNIQUE (public_id),
    CONSTRAINT fk_assignments_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_assignments_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_assignments_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
);
CREATE INDEX idx_assignments_status ON assignments (status);
CREATE INDEX idx_assignments_candidate ON assignments (candidate_id);

CREATE TABLE blueprints (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(40)  NOT NULL,
    assignment_id   BIGINT       NOT NULL,
    version_no      INT          NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    content_json    MEDIUMTEXT   NOT NULL,
    approved_by     VARCHAR(255),
    approved_at     DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_blueprints_public_id UNIQUE (public_id),
    CONSTRAINT uk_blueprints_version UNIQUE (assignment_id, version_no),
    CONSTRAINT fk_blueprints_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id)
);

CREATE TABLE blueprint_requirements (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    blueprint_id                BIGINT        NOT NULL,
    code                        VARCHAR(16)   NOT NULL,
    title                       VARCHAR(255)  NOT NULL,
    description                 VARCHAR(4000) NOT NULL,
    category                    VARCHAR(32)   NOT NULL,
    mandatory                   BOOLEAN       NOT NULL,
    acceptance_criteria_json    MEDIUMTEXT    NOT NULL,
    CONSTRAINT uk_requirements_code UNIQUE (blueprint_id, code),
    CONSTRAINT fk_requirements_blueprint FOREIGN KEY (blueprint_id) REFERENCES blueprints (id)
);

CREATE TABLE projects (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(40)  NOT NULL,
    assignment_id   BIGINT       NOT NULL,
    blueprint_id    BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    template_key    VARCHAR(64)  NOT NULL,
    manifest_json   MEDIUMTEXT   NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_projects_public_id UNIQUE (public_id),
    CONSTRAINT uk_projects_assignment UNIQUE (assignment_id),
    CONSTRAINT fk_projects_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_projects_blueprint FOREIGN KEY (blueprint_id) REFERENCES blueprints (id)
);

CREATE TABLE github_repositories (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id           VARCHAR(40)  NOT NULL,
    assignment_id       BIGINT       NOT NULL,
    project_id          BIGINT       NOT NULL,
    owner               VARCHAR(100) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    github_repo_id      BIGINT,
    html_url            VARCHAR(512),
    default_branch      VARCHAR(100) NOT NULL,
    webhook_id          BIGINT,
    status              VARCHAR(32)  NOT NULL,
    candidate_access    VARCHAR(32)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uk_repos_public_id UNIQUE (public_id),
    CONSTRAINT uk_repos_assignment UNIQUE (assignment_id),
    CONSTRAINT uk_repos_full_name UNIQUE (owner, name),
    CONSTRAINT fk_repos_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_repos_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE TABLE push_events (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    repository_id       BIGINT       NOT NULL,
    delivery_id         VARCHAR(64)  NOT NULL,
    ref_name            VARCHAR(255) NOT NULL,
    before_sha          CHAR(40),
    after_sha           CHAR(40)     NOT NULL,
    pusher_login        VARCHAR(100),
    forced              BOOLEAN      NOT NULL,
    commit_count        INT          NOT NULL,
    head_commit_message VARCHAR(2000),
    received_at         DATETIME(6)  NOT NULL,
    CONSTRAINT uk_push_events_delivery UNIQUE (delivery_id),
    CONSTRAINT fk_push_events_repo FOREIGN KEY (repository_id) REFERENCES github_repositories (id)
);

CREATE TABLE ci_runs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    repository_id   BIGINT       NOT NULL,
    github_run_id   BIGINT       NOT NULL,
    head_sha        CHAR(40)     NOT NULL,
    workflow_name   VARCHAR(255),
    status          VARCHAR(32)  NOT NULL,
    conclusion      VARCHAR(32),
    html_url        VARCHAR(512),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_ci_runs_run UNIQUE (github_run_id),
    CONSTRAINT fk_ci_runs_repo FOREIGN KEY (repository_id) REFERENCES github_repositories (id)
);

CREATE TABLE submissions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(40)  NOT NULL,
    assignment_id   BIGINT       NOT NULL,
    attempt_no      INT          NOT NULL,
    commit_sha      CHAR(40)     NOT NULL,
    tag_ref         VARCHAR(255) NOT NULL,
    trigger_source  VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    submitted_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_submissions_public_id UNIQUE (public_id),
    CONSTRAINT uk_submissions_attempt UNIQUE (assignment_id, attempt_no),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id)
);

CREATE TABLE evaluation_runs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(40)  NOT NULL,
    submission_id   BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    pipeline_run_id BIGINT,
    pipeline_url    VARCHAR(512),
    result_json     MEDIUMTEXT,
    dispatched_at   DATETIME(6)  NOT NULL,
    completed_at    DATETIME(6),
    CONSTRAINT uk_evaluation_runs_public_id UNIQUE (public_id),
    CONSTRAINT fk_evaluation_runs_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
);

CREATE TABLE code_analyses (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id           VARCHAR(40)  NOT NULL,
    submission_id       BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    result_json         MEDIUMTEXT,
    files_analyzed      INT          NOT NULL DEFAULT 0,
    files_skipped_json  MEDIUMTEXT,
    created_at          DATETIME(6)  NOT NULL,
    completed_at        DATETIME(6),
    CONSTRAINT uk_code_analyses_public_id UNIQUE (public_id),
    CONSTRAINT uk_code_analyses_submission UNIQUE (submission_id),
    CONSTRAINT fk_code_analyses_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
);

CREATE TABLE mcq_assessments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id           VARCHAR(40)  NOT NULL,
    assignment_id       BIGINT       NOT NULL,
    submission_id       BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    time_limit_minutes  INT          NOT NULL,
    started_at          DATETIME(6),
    deadline_at         DATETIME(6),
    completed_at        DATETIME(6),
    score               INT,
    max_score           INT,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uk_mcq_assessments_public_id UNIQUE (public_id),
    CONSTRAINT uk_mcq_assessments_submission UNIQUE (submission_id),
    CONSTRAINT fk_mcq_assessments_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_mcq_assessments_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
);

CREATE TABLE mcq_questions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    assessment_id       BIGINT        NOT NULL,
    code                VARCHAR(16)   NOT NULL,
    position            INT           NOT NULL,
    category            VARCHAR(32)   NOT NULL,
    stem                VARCHAR(4000) NOT NULL,
    options_json        MEDIUMTEXT    NOT NULL,
    correct_option      INT           NOT NULL,
    explanation         VARCHAR(4000),
    source_ref          VARCHAR(512),
    requirement_code    VARCHAR(16),
    CONSTRAINT uk_mcq_questions_code UNIQUE (assessment_id, code),
    CONSTRAINT fk_mcq_questions_assessment FOREIGN KEY (assessment_id) REFERENCES mcq_assessments (id)
);

CREATE TABLE mcq_answers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id     BIGINT      NOT NULL,
    selected_option INT         NOT NULL,
    correct         BOOLEAN     NOT NULL,
    answered_at     DATETIME(6) NOT NULL,
    CONSTRAINT uk_mcq_answers_question UNIQUE (question_id),
    CONSTRAINT fk_mcq_answers_question FOREIGN KEY (question_id) REFERENCES mcq_questions (id)
);

CREATE TABLE final_reports (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(40)  NOT NULL,
    assignment_id   BIGINT       NOT NULL,
    submission_id   BIGINT       NOT NULL,
    content_json    MEDIUMTEXT   NOT NULL,
    generated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_final_reports_public_id UNIQUE (public_id),
    CONSTRAINT uk_final_reports_assignment UNIQUE (assignment_id),
    CONSTRAINT fk_final_reports_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_final_reports_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
);

CREATE TABLE audit_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id   BIGINT       NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    actor           VARCHAR(255) NOT NULL,
    detail          VARCHAR(4000),
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_audit_events_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id)
);
CREATE INDEX idx_audit_events_assignment ON audit_events (assignment_id, created_at);
