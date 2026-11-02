// ABOUTME: Unit tests for FinishedUploads: an inbound file counts as finished once two polls see it unchanged.
// ABOUTME: Growing or touched files wait, and forgetting the observations restarts the check for every file.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.intake.SftpChannel.RemoteFile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinishedUploadsTest {

    private static final Instant T0 = Instant.parse("2026-10-31T10:00:00Z");

    private final FinishedUploads uploads = new FinishedUploads();

    @Test
    void aFileSeenForTheFirstTimeIsNotFinished() {
        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)))).isEmpty();
    }

    @Test
    void aFileUnchangedSinceThePreviousPollIsFinished() {
        uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)));

        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)))).containsExactly("a.xml");
    }

    @Test
    void aFileThatGrewIsNotFinished() {
        uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)));

        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 200, T0)))).isEmpty();
        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 200, T0)))).containsExactly("a.xml");
    }

    @Test
    void aFileWithANewModificationTimeIsNotFinished() {
        uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)));

        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 100, T0.plusSeconds(1)))))
                .isEmpty();
    }

    @Test
    void judgesEveryFileOnItsOwn() {
        uploads.finished(List.of(new RemoteFile("done.xml", 100, T0), new RemoteFile("growing.xml", 10, T0)));

        assertThat(uploads.finished(List.of(
                        new RemoteFile("done.xml", 100, T0),
                        new RemoteFile("growing.xml", 20, T0),
                        new RemoteFile("new.xml", 5, T0))))
                .containsExactly("done.xml");
    }

    @Test
    void aFileThatDisappearedAndCameBackStartsOver() {
        uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)));
        uploads.finished(List.of());

        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)))).isEmpty();
    }

    @Test
    void forgettingRestartsTheCheck() {
        uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)));

        uploads.forget();

        assertThat(uploads.finished(List.of(new RemoteFile("a.xml", 100, T0)))).isEmpty();
    }
}
