// Response shapes from the backend. Kept in sync with the Java records by hand.

export type AssignmentSummary = {
  assignmentId: string;
  status: string;
  candidateId: string;
  candidateName: string;
  jobTitle: string;
  companyName: string;
  createdAt: string;
  dueAt: string | null;
};

export type Page<T> = { content: T[]; totalElements: number; number: number; totalPages: number };

export type ProjectConfiguration = {
  domain: string;
  projectType: string;
  complexity: "BASIC" | "INTERMEDIATE" | "ADVANCED";
  durationDays: number;
  technologies: string[];
  mandatoryRequirements: string[];
  optionalRequirements: string[];
  notes?: string | null;
};

export type SkillAssessment = {
  skill: string;
  requiredByJob: boolean;
  requiredLevel: string;
  candidateLevel: string;
  evidence: string;
};

export type RequirementAnalysis = {
  summary: string;
  domain: string;
  skills: SkillAssessment[];
  skillGaps: string[];
  recommendedTechnologies: string[];
  recommendedComplexity: ProjectConfiguration["complexity"];
  requiredCapabilities: string[];
  projectIdeas: { title: string; rationale: string }[];
};

export type AssignmentDetail = {
  summary: AssignmentSummary;
  failureReason: string | null;
  durationDays: number | null;
  assignedAt: string | null;
  githubUsername: string | null;
  requirementAnalysis: RequirementAnalysis | null;
  projectConfiguration: ProjectConfiguration | null;
  repository: { repositoryId: string; fullName: string; url: string | null; status: string; candidateAccess: string } | null;
  submissions: { submissionId: string; attempt: number; commitSha: string; tagRef: string; trigger: string; submittedAt: string }[];
};

export type Requirement = {
  code: string;
  title: string;
  description: string;
  category: string;
  mandatory: boolean;
  acceptanceCriteria: string[];
};

export type BlueprintContent = {
  projectName: string;
  businessProblem: string;
  scope: string;
  modules: { name: string; description: string }[];
  requirements: Requirement[];
  apis: { method: string; path: string; description: string; requirementCodes: string[] }[];
  dataModel: { name: string; description?: string; fields?: string[] }[];
  events: { name: string; topic: string; producer?: string; consumer?: string; payload?: string; requirementCodes?: string[] }[];
  nonFunctionalRequirements: string[];
  securityRequirements: string[];
  testingRequirements: string[];
  evaluationCriteria: string[];
  candidateInstructions: string;
};

export type Blueprint = {
  blueprintId: string;
  version: number;
  status: "DRAFT" | "APPROVED" | "SUPERSEDED";
  approvedBy: string | null;
  approvedAt: string | null;
  createdAt: string;
  content: BlueprintContent;
};

export type AuditEvent = { type: string; actor: string; detail: string | null; at: string };

export type Stage = { name: string; outcome: "PASSED" | "FAILED" | "SKIPPED" | "ERROR"; summary: string | null };
export type TestSummary = { total: number; passed: number; failed: number; skipped: number };

export type Report = {
  reportId: string;
  generatedAt: string;
  candidate: { candidateId: string; name: string; jobId: string; jobTitle: string; companyId: string; companyName: string };
  project: { assignmentId: string; projectId: string; blueprintId: string; name: string; domain: string; complexity: string; technologies: string[] };
  repository: { repositoryId: string; fullName: string; url: string; submissionId: string; commitSha: string; tagRef: string; submittedAt: string; submittedBy: string };
  automatedEvaluation: { evaluationId: string; pipelineUrl: string | null; stages: Stage[]; visibleTests: TestSummary | null; hiddenTests: TestSummary | null; failedTests: string[] | null } | null;
  requirements: { code: string; title: string; mandatory: boolean; status: string; implementationRefs: string[]; testRefs: string[]; notes: string | null }[];
  codeQuality: {
    codeAnalysisId: string;
    summary: string;
    areas: { area: string; score: number; strengths: string[]; issues: string[] }[];
    findings: { severity: string; area: string; file: string; symbol: string; description: string; recommendation: string }[];
    filesAnalyzed: number;
    filesSkipped: { path: string; reason: string }[];
  } | null;
  mcq: {
    assessmentId: string;
    total: number;
    correct: number;
    byCategory: Record<string, { correct: number; total: number }>;
    questions: { code: string; category: string; stem: string; options: string[]; selectedOption: number | null; correctOption: number; correct: boolean; sourceRef: string | null; requirementCode: string | null; explanation: string | null }[];
  };
  scores: { requirements: number | null; automatedEvaluation: number | null; codeQuality: number | null; mcq: number | null; overall: number | null; weights: Record<string, number> };
};

export type CandidateAssignment = { assignmentId: string; jobTitle: string; companyName: string; status: string; dueAt: string | null };

export type Dashboard = {
  assignmentId: string;
  status: string;
  jobTitle: string;
  companyName: string;
  project: {
    name: string;
    domain: string;
    technologies: string[];
    durationDays: number;
    assignedAt: string | null;
    dueAt: string | null;
    businessProblem: string;
    instructions: string;
    requirements: { code: string; title: string; description: string; mandatory: boolean; acceptanceCriteria: string[] }[];
  } | null;
  repository: { fullName: string; url: string | null; cloneUrl: string | null; access: string; githubUsername: string | null } | null;
  ciRuns: { commitSha: string; workflow: string | null; status: string; conclusion: string | null; url: string | null; updatedAt: string }[];
  recentPushes: { commitSha: string; message: string | null; commits: number; at: string }[];
  submission: { submissionId: string; commitSha: string; submittedAt: string; trigger: string } | null;
  assessment: { status: string; questionCount: number; timeLimitMinutes: number; deadlineAt: string | null } | null;
};

export type AssessmentAttempt = {
  assessmentId: string;
  status: "READY" | "IN_PROGRESS" | "COMPLETED";
  deadlineAt: string | null;
  questions: { code: string; position: number; category: string; stem: string; options: string[]; selectedOption: number | null }[];
};
