package com.geekyants.assessment.codeanalysis;

import com.geekyants.assessment.github.GitHubClient.TreeEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chooses which files to send to analysis within a character budget. Main sources first, then tests,
 * then build and config files. Everything not chosen is reported, never silently dropped.
 */
public final class SourceSelector {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "kt", "xml", "yml", "yaml", "properties", "sql", "json", "md", "sh", "gradle", "kts");
    private static final Set<String> TEXT_NAMES = Set.of("Dockerfile", "docker-compose.yml", "compose.yaml");
    private static final long MAX_FILE_BYTES = 120_000;

    private SourceSelector() {
    }

    public record Skipped(String path, String reason) {
    }

    public record Selection(List<TreeEntry> selected, List<Skipped> skipped) {
    }

    /** {@code budget} is in bytes; blob sizes are bytes and sources are overwhelmingly ASCII. */
    public static Selection select(List<TreeEntry> files, int budget) {
        List<TreeEntry> candidates = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (TreeEntry f : files) {
            if (f.path().startsWith("target/") || f.path().contains("/target/")) {
                skipped.add(new Skipped(f.path(), "build output"));
            } else if (!isText(f.path())) {
                skipped.add(new Skipped(f.path(), "not a source or config file"));
            } else if (f.size() > MAX_FILE_BYTES) {
                skipped.add(new Skipped(f.path(), "larger than " + MAX_FILE_BYTES + " bytes"));
            } else {
                candidates.add(f);
            }
        }
        candidates.sort(Comparator.comparingInt((TreeEntry f) -> priority(f.path())).thenComparing(TreeEntry::path));

        List<TreeEntry> selected = new ArrayList<>();
        long used = 0;
        for (TreeEntry f : candidates) {
            if (used + f.size() > budget) {
                skipped.add(new Skipped(f.path(), "analysis size budget reached"));
            } else {
                selected.add(f);
                used += f.size();
            }
        }
        return new Selection(selected, skipped);
    }

    static int priority(String path) {
        if (path.startsWith("src/main/")) {
            return 0;
        }
        if (path.startsWith("src/test/")) {
            return 1;
        }
        if (!path.contains("/")) {
            return 2;
        }
        // Generated docs/ come last: the candidate may have edited them, but they are mostly ours.
        return path.startsWith("docs/") ? 4 : 3;
    }

    private static boolean isText(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (TEXT_NAMES.contains(name)) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
