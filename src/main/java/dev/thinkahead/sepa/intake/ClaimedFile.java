// ABOUTME: A claimed file, named "<file id>-<name>" in processing, "<file id>.instant-<name>" from the SCT Inst inbox.
// ABOUTME: The file id in the name lets receiving and archiving be repeated safely after a crash (spec file-intake).
package dev.thinkahead.sepa.intake;

import java.util.Optional;
import java.util.UUID;

record ClaimedFile(UUID fileId, String originalName, boolean instant) {

    private static final int UUID_LENGTH = 36;
    private static final String REGULAR_SEPARATOR = "-";
    private static final String INSTANT_SEPARATOR = ".instant-";

    /**
     * A file claimed from the regular inbox.
     */
    ClaimedFile(UUID fileId, String originalName) {
        this(fileId, originalName, false);
    }

    static Optional<ClaimedFile> parse(String claimedName) {
        if (claimedName.length() <= UUID_LENGTH + 1) {
            return Optional.empty();
        }
        String rest = claimedName.substring(UUID_LENGTH);
        boolean instant = rest.startsWith(INSTANT_SEPARATOR) && rest.length() > INSTANT_SEPARATOR.length();
        if (!instant && !rest.startsWith(REGULAR_SEPARATOR)) {
            return Optional.empty();
        }
        try {
            UUID fileId = UUID.fromString(claimedName.substring(0, UUID_LENGTH));
            String separator = instant ? INSTANT_SEPARATOR : REGULAR_SEPARATOR;
            return Optional.of(new ClaimedFile(fileId, rest.substring(separator.length()), instant));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    String claimedName() {
        return fileId + (instant ? INSTANT_SEPARATOR : REGULAR_SEPARATOR) + originalName;
    }
}
