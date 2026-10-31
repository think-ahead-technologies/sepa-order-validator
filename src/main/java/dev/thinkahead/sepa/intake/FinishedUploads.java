// ABOUTME: Tells finished uploads apart from files a customer is still writing into SFTP inbound.
// ABOUTME: A file counts as finished once its size and modification time are unchanged since the previous poll.
package dev.thinkahead.sepa.intake;

import dev.thinkahead.sepa.intake.SftpChannel.RemoteFile;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Remembers what the previous poll saw. Used only by the single polling thread, so it is not thread-safe.
 * A stall in the upload longer than a poll interval cannot be told apart from a finished upload.
 */
final class FinishedUploads {

    private Map<String, RemoteFile> previousPoll = Map.of();

    /**
     * Records the files seen in this poll.
     *
     * @return names of the files that are unchanged since the previous poll, in the given order
     */
    List<String> finished(List<RemoteFile> currentPoll) {
        List<String> unchanged = currentPoll.stream()
                .filter(file -> file.equals(previousPoll.get(file.name())))
                .map(RemoteFile::name)
                .toList();
        previousPoll = currentPoll.stream().collect(Collectors.toMap(RemoteFile::name, Function.identity()));
        return unchanged;
    }

    /**
     * Drops all observations, e.g. when leadership is lost, so stale observations are never trusted.
     */
    void forget() {
        previousPoll = Map.of();
    }
}
