// ABOUTME: How a payment file was submitted: through the SEPA Instant inbox and/or as a message of a DK XML container.
// ABOUTME: Validation checks the content against it, as DK order types CCT and CIP and the container rules require.
package dev.thinkahead.sepa.intake;

/**
 * @param instantOrder  submitted through the SEPA Instant inbox (DK order type CIP), or split from a container that was
 * @param fromContainer split from a DK XML container
 */
public record Submission(boolean instantOrder, boolean fromContainer) {

    /**
     * A single message from the regular inbox.
     */
    public static final Submission REGULAR = new Submission(false, false);
}
