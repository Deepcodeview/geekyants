#!/usr/bin/env python3
"""Builds the evaluation callback payload from the job artifacts. Standard library only."""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

OUTCOME = {"success": "PASSED", "failure": "FAILED", "skipped": "SKIPPED", "cancelled": "ERROR", "": "SKIPPED"}


def load(path, default):
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def junit(directory):
    total = passed = failed = skipped = 0
    failures = []
    for path in glob.glob(os.path.join(directory, "*.xml")):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            total += 1
            name = f"{case.get('classname')}.{case.get('name')}"
            if case.find("skipped") is not None:
                skipped += 1
            elif case.find("failure") is not None or case.find("error") is not None:
                failed += 1
                failures.append(name)
            else:
                passed += 1
    return {"total": total, "passed": passed, "failed": failed, "skipped": skipped}, failures


def main(root):
    black = load(os.path.join(root, "blackbox-results", "blackbox-stages.json"), {})
    white = load(os.path.join(root, "whitebox-results", "stages.json"), {})
    hidden = load(os.path.join(root, "blackbox-results", "hidden-tests.json"), None)

    unit, unit_failures = junit(os.path.join(root, "whitebox-results", "reports"))
    it, it_failures = junit(os.path.join(root, "whitebox-results", "it-reports"))
    visible = {k: unit[k] + it[k] for k in unit}

    def stage(name, outcome, summary=None):
        return {"name": name, "outcome": OUTCOME.get(outcome or "", "ERROR"), "summary": summary}

    fetch_ok = os.environ.get("FETCH_RESULT") == "success"
    stages = [
        stage("checkout", "success" if fetch_ok else "failure"),
        stage("build", white.get("build")),
        stage("unit_tests", "failure" if unit["failed"] else white.get("tests"),
              f"{unit['passed']}/{unit['total'] - unit['skipped']} passed"),
        stage("integration_tests", "failure" if it["failed"] else ("success" if it["total"] else "skipped"),
              f"{it['passed']}/{it['total'] - it['skipped']} passed"),
        stage("static_analysis", white.get("static_analysis")),
        stage("security_scan", white.get("security_scan")),
        stage("docker_build", black.get("docker_build")),
        stage("deploy", black.get("deploy")),
        stage("smoke_tests", black.get("smoke_tests")),
        stage("api_tests", black.get("api_tests")),
    ]

    result = {
        "evaluationId": os.environ["EVALUATION_ID"],
        "commitSha": os.environ["COMMIT_SHA"],
        "runId": int(os.environ["RUN_ID"]),
        "runUrl": os.environ["RUN_URL"],
        "stages": stages,
        "visibleTests": visible,
        "hiddenTests": None if hidden is None else {k: hidden[k] for k in ("total", "passed", "failed", "skipped")},
        # Hidden test names are included for reviewers; the platform never shows them to candidates.
        "failedTests": unit_failures + it_failures + ([] if hidden is None else hidden.get("failedTests", [])),
    }
    json.dump(result, sys.stdout)


if __name__ == "__main__":
    main(sys.argv[1])
