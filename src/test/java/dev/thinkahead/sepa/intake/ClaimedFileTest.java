// ABOUTME: Unit tests for the naming of claimed files: "<file id>-<original name>" in the processing directory.
// ABOUTME: The file id in the name is what makes intake safe to repeat after a crash.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimedFileTest {

    private static final UUID FILE_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @Test
    void claimedNamePrefixesTheOriginalNameWithTheFileId() {
        assertThat(new ClaimedFile(FILE_ID, "payroll.xml").claimedName())
                .isEqualTo("0f8fad5b-d9cb-469f-a165-70867728950e-payroll.xml");
    }

    @Test
    void parsesClaimedNameBackIntoFileIdAndOriginalName() {
        assertThat(ClaimedFile.parse("0f8fad5b-d9cb-469f-a165-70867728950e-payroll-2026-10.xml"))
                .contains(new ClaimedFile(FILE_ID, "payroll-2026-10.xml"));
    }

    @Test
    void keepsOriginalNamesThatThemselvesStartWithAUuid() {
        String original = UUID.randomUUID() + "-export.xml";

        assertThat(ClaimedFile.parse(new ClaimedFile(FILE_ID, original).claimedName()))
                .contains(new ClaimedFile(FILE_ID, original));
    }

    @Test
    void rejectsNamesWithoutAFileIdPrefix() {
        assertThat(ClaimedFile.parse("payroll.xml")).isEmpty();
        assertThat(ClaimedFile.parse("not-a-uuid-at-all-not-a-uuid-at-all-x.xml")).isEmpty();
        assertThat(ClaimedFile.parse("0f8fad5b-d9cb-469f-a165-70867728950e-")).isEmpty();
    }
}
