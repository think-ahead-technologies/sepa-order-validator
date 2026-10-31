// ABOUTME: A file claimed for intake, named "<file id>-<original name>" in the channel's processing directory.
// ABOUTME: The file id in the name lets receiving and archiving be repeated safely after a crash (ADR-0007).
package dev.thinkahead.sepa.intake;

import java.util.Optional;
import java.util.UUID;

record ClaimedFile(UUID fileId, String originalName) {

    private static final int UUID_LENGTH = 36;

    static Optional<ClaimedFile> parse(String claimedName) {
        if (claimedName.length() <= UUID_LENGTH + 1 || claimedName.charAt(UUID_LENGTH) != '-') {
            return Optional.empty();
        }
        try {
            UUID fileId = UUID.fromString(claimedName.substring(0, UUID_LENGTH));
            return Optional.of(new ClaimedFile(fileId, claimedName.substring(UUID_LENGTH + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    String claimedName() {
        return fileId + "-" + originalName;
    }
}
