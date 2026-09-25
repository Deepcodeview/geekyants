# AI Customized Project Assessment Platform

Receives a job description and candidate profile from the recruitment system, designs a take-home project
for that candidate, provisions a private GitHub repository, evaluates the exact commit the candidate submits,
asks 12–15 questions about their own code, and produces a traceable final report.

```
assessment-platform/
├── backend/            Java 21 · Spring Boot 4.1 · MySQL · Flyway · JPA · Spring Security (JWT/OIDC)
├── frontend/           Next.js 16 · React 19 · TypeScript — admin and candidate UIs
├── evaluation-runner/  Contents of the platform-owned GitHub repo that runs final evaluations
└── docker-compose.yml  Local MySQL
```

## Lifecycle

One assignment moves through a strict state machine
([AssignmentStatus.java](backend/src/main/java/com/geekyants/assessment/assignment/AssignmentStatus.java)).
Slow steps (LLM calls, GitHub, CI) run asynchronously after the triggering transaction commits, so the
API never holds a database transaction open while waiting on them.

| # | Step | State after | Where |
|---|------|-------------|-------|
| 1 | Recruitment system posts company + job + candidate | `ANALYZING_REQUIREMENTS` | `intake/` |
| 2 | LLM compares JD and profile: skill levels with evidence, gaps, complexity | `ANALYZED` | `analysis/` |
| 3 | Admin configures domain, type, complexity, duration, stack, requirements | — | `blueprint/` |
| 4 | LLM writes the blueprint (REQ-001…, APIs, data model, events, criteria) | `BLUEPRINT_DRAFT` | `blueprint/` |
| 5 | Admin edits (each edit is a new version) or regenerates, then approves | `PROVISIONING` | `blueprint/` |
| 6 | Project rendered from a vetted template + docs generated from the blueprint | — | `generation/` |
| 7 | Private repo created, skeleton committed, rulesets + webhook added | `AWAITING_GITHUB_USERNAME` | `github/` |
| 8 | Candidate links GitHub account → invited to that repo only; clock starts | `ASSIGNED` | `candidate/`, `github/` |
| 9 | Pushes arrive via signed webhook; feedback CI shown on dashboard | `IN_PROGRESS` | `webhook/` |
| 10 | Candidate submits (or deadline auto-submits): write access revoked → head read → tag created | `SUBMITTED` | `submission/` |
| 11 | Platform dispatches the evaluation workflow for that SHA; runner posts signed results | `ANALYZING_CODE` | `evaluation/` |
| 12 | LLM maps requirements → code → tests on the frozen commit | `GENERATING_MCQ` | `codeanalysis/` |
| 13 | LLM writes 12–15 MCQs, at least half citing the candidate's own code | `MCQ_READY` | `mcq/` |
| 14 | Candidate takes the timed MCQ; scored server-side | `MCQ_COMPLETED` | `mcq/` |
| 15 | Final report assembled with deterministic scores and full traceability | `REPORTED` | `report/` |

Any platform-side failure moves the assignment to `FAILED` with the reason; `POST /admin/assignments/{id}/retry`
resumes at the step that failed. Every step is written to be re-run safely (provisioning, for example, records
progress per sub-step and skips what is already done).

Every state change and key action is written to `audit_events`, and every object carries a public ID
(`ASSIGN-`, `BP-`, `PROJ-`, `REPO-`, `SUB-`, `EVAL-`, `CA-`, `ASMT-`, `MCQ-001`, `RPT-`).

## Design decisions worth knowing

- **Submission freeze.** On submit the platform first downgrades the candidate to read access, then reads the
  branch head, then creates `refs/tags/submission-SUB-…` at that SHA. Revoking first means no push can land
  between reading and tagging. A tag ruleset blocks updating or deleting submission tags, and a branch ruleset
  blocks force-push, so the frozen commit stays reachable.
- **Candidate CI is never trusted.** The candidate can edit `.github/workflows/ci.yml`, so it has no secrets and
  its results are shown as feedback only. Scoring comes from the platform-owned `evaluation-runner` workflow.
- **Evaluation isolation.** The runner splits into three jobs: `fetch` (holds the read token, runs no candidate
  code), `evaluate` (runs candidate code, no secrets, no token permissions) and `report` (holds the callback
  secret, runs no candidate code). Black-box stages (Docker build, deploy, smoke, hidden API tests) run and
  upload their results *before* candidate code executes on the runner host, so candidate tests cannot tamper with
  them. The callback is HMAC-signed and must name the frozen SHA or it is rejected.
- **Skeletons come from templates, not the LLM.** The LLM designs the blueprint; the repository is rendered
  deterministically from `backend/src/main/resources/templates/java-spring-boot`, so every candidate starts
  from a build known to pass (`ProjectGeneratorTest` can write it out; it builds with `mvn verify`).
- **Structured LLM output.** Every LLM call uses Claude structured outputs into a Java record, then runs extra
  validation (requirement codes, MCQ count/options/coverage). Invalid output fails the step instead of being
  stored. Candidate code is passed as data, and the prompts tell the model to flag prompt-injection attempts
  rather than follow them.
- **MCQ answer keys never leave the server.** Options are shuffled server-side (models favour certain answer
  positions), questions are only returned while the timed attempt is running, and expired attempts are finished
  by a scheduler.
- **Nothing is silently dropped from analysis.** Files outside the size budget are listed in the report as skipped.
- **Scores support reviewers; they don't make the decision.** The overall score is a weighted blend
  (requirements 35, automated 25, code quality 15, MCQ 25) over the components that could be measured.

## Running locally

Prerequisites: JDK 21+, Maven 3.9+, Node 20+, Docker.

```bash
docker compose up -d                                  # MySQL on :3306

cd backend
export ANTHROPIC_API_KEY=...                          # needed for analysis / blueprint / MCQ steps
export GITHUB_TOKEN=... GITHUB_ORG=... GITHUB_WEBHOOK_SECRET=... EVALUATION_CALLBACK_SECRET=...
mvn spring-boot:run -Dspring-boot.run.profiles=dev    # API on :8080, Flyway migrates the schema

cd ../frontend
cp .env.example .env.local
npm install && npm run dev                            # UI on :3000
```

The `dev` profile enables `POST /dev/tokens`, which mints local JWTs; the sign-in page uses it. Without the
`dev` profile the backend requires `OIDC_ISSUER_URI` and refuses to start otherwise.

Create an intake token and an assignment:

```bash
TOKEN=$(curl -s localhost:8080/dev/tokens -H 'Content-Type: application/json' \
  -d '{"subject":"recruitment-system","roles":["INTEGRATION"]}' | jq -r .token)

curl -s localhost:8080/api/v1/intake/assignments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
  "company":   {"companyId":"ABC-BANK","name":"ABC Bank","industry":"Finance","domain":"Banking"},
  "job":       {"jobId":"JOB-1","title":"Java Backend Developer","seniority":"Senior",
                "requiredSkills":["Java","Spring Boot","Hibernate","MySQL","Kafka","REST API"]},
  "candidate": {"candidateId":"CAND-7821","fullName":"Jane Doe","githubUsername":"janedoe",
                "skills":["Java","Spring Boot","MySQL"],"yearsOfExperience":6}
}'
```

### Tests

```bash
cd backend && mvn verify
```

- Unit tests: state machine, HMAC verification, blueprint and MCQ validation, source selection, coverage
  alignment, scoring, project generation.
- `AssessmentLifecycleIT`: drives one assignment from intake to final report on H2 with GitHub and the LLM
  mocked. It checks role and ownership rules, rejection of unsigned webhooks and wrong-commit callbacks, the
  revoke → read head → tag order, double-submit rejection, and that answer keys never reach the candidate.

## API

| Method | Path | Role |
|--------|------|------|
| POST | `/api/v1/intake/assignments` | INTEGRATION |
| GET | `/api/v1/admin/assignments`, `/{id}`, `/{id}/audit`, `/{id}/blueprints`, `/{id}/report` | ADMIN |
| POST | `/api/v1/admin/assignments/{id}/analysis/rerun` | ADMIN |
| PUT | `/api/v1/admin/assignments/{id}/configuration` | ADMIN |
| POST | `/api/v1/admin/assignments/{id}/blueprints/generate` `{instructions?}` | ADMIN |
| PUT | `/api/v1/admin/assignments/{id}/blueprints/draft` (full blueprint → new version) | ADMIN |
| POST | `/api/v1/admin/assignments/{id}/blueprints/{blueprintId}/approve` | ADMIN |
| POST | `/api/v1/admin/assignments/{id}/retry` | ADMIN |
| GET | `/api/v1/candidate/assignments`, `/{id}` | CANDIDATE |
| PUT | `/api/v1/candidate/github-username` | CANDIDATE |
| POST | `/api/v1/candidate/assignments/{id}/submit` | CANDIDATE |
| GET/POST | `/api/v1/candidate/assignments/{id}/assessment`, `/start`, `/finish` | CANDIDATE |
| PUT | `/api/v1/candidate/assignments/{id}/assessment/answers/{MCQ-001}` | CANDIDATE |
| POST | `/api/v1/webhooks/github` | HMAC (`X-Hub-Signature-256`) |
| POST | `/api/v1/callbacks/evaluations/{evaluationId}` | HMAC (`X-Signature-256`) |

Roles come from the JWT `roles` claim; candidate tokens also carry `candidate_id`, and candidates can only reach
their own assignments (others return 404).

## GitHub setup

1. Create an organization (e.g. `assessment-platform`). Private-repo rulesets need **GitHub Team or Enterprise**;
   on Free, set `GITHUB_ENFORCE_RULESETS=false` and rely on read-only access + tags (weaker: see gaps below).
2. Give the backend a token (`GITHUB_TOKEN`) with repository administration, contents, webhooks, and actions
   (workflow dispatch) on the org. A GitHub App installation token is preferable to a PAT.
3. Create a private repo `evaluation-runner` from [evaluation-runner/](evaluation-runner/) with secrets:
   - `CANDIDATE_REPO_READ_TOKEN` — read-only contents access to `assessment-*` repos
   - `EVALUATION_CALLBACK_SECRET` — same value as the backend's `EVALUATION_CALLBACK_SECRET`
4. `APP_PUBLIC_BASE_URL` must be reachable from GitHub for webhooks and callbacks.

## Known gaps and next steps

- **GitHub account ownership isn't verified.** The candidate types a username; the invitation goes to that
  account. Replace with GitHub OAuth so the platform confirms the candidate controls it (`CandidateService`).
- **Hidden tests are generic.** `evaluation-runner/kit/hidden-tests/java-spring-boot/run.sh` has baseline
  checks; per-blueprint hidden tests (duplicate transactions, auth, Kafka events) still need to be written or
  generated from acceptance criteria.
- **Runner egress.** On GitHub-hosted runners candidate code has internet access and could exfiltrate hidden
  tests. Use a self-hosted runner with egress restricted to package registries.
- **Only one stack template** (Java + Spring Boot, optional MySQL/JPA and Kafka). Configuration rejects other stacks.
- **Single submission per assignment.** The schema supports attempts (`attempt_no`); reopening an assignment
  for resubmission isn't implemented.
- **Fairness.** Tailoring projects to each candidate's gaps means candidates for the same job get different
  projects. Consider a common core per job with a small tailored part, and have legal review the approach.
- **Candidate data goes to the LLM provider.** Resume text is sent to Claude; confirm consent and retention
  terms. The prompts exclude personal attributes from judgments.
- **Dev auth in the frontend** stores the token in localStorage. Replace with OIDC login before production.
- **Async execution** uses Spring's in-process `@Async`; a restart mid-step leaves the assignment in its `*-ING`
  state. After 15 minutes without progress an admin retry resumes it. Move to a durable queue (or an outbox + worker) before running at scale.
- **Evaluation timeout** is 90 minutes; runs that never report back move the assignment to `FAILED`.
