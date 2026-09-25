package com.geekyants.assessment.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.geekyants.assessment.github.GitHubClient.TreeEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceSelectorTest {

    @Test
    void prioritisesMainSourcesAndReportsEverythingSkipped() {
        List<TreeEntry> files = List.of(
                new TreeEntry("docs/API.md", "1", 100),
                new TreeEntry("src/test/java/AppTest.java", "2", 300),
                new TreeEntry("src/main/java/App.java", "3", 400),
                new TreeEntry("pom.xml", "4", 200),
                new TreeEntry("logo.png", "5", 10),
                new TreeEntry("target/classes/App.class", "6", 10));

        SourceSelector.Selection selection = SourceSelector.select(files, 800);

        assertThat(selection.selected()).extracting(TreeEntry::path)
                .containsExactly("src/main/java/App.java", "src/test/java/AppTest.java", "docs/API.md");
        assertThat(selection.skipped()).extracting(SourceSelector.Skipped::path)
                .containsExactlyInAnyOrder("logo.png", "target/classes/App.class", "pom.xml");
        assertThat(selection.skipped()).anyMatch(s -> s.path().equals("pom.xml")
                && s.reason().equals("analysis size budget reached"));
    }
}
