// ABOUTME: Accepts a payment file from a channel: hashes and stores it, then publishes PaymentFileReceived.
// ABOUTME: Storage and event publication share one transaction, so no received file is ever lost (ADR-0007).
package dev.thinkahead.sepa.intake;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentFileIntake {

    private final PaymentFileRepository repository;
    private final ApplicationEventPublisher events;

    PaymentFileIntake(PaymentFileRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    UUID receive(Channel channel, String fileName, byte[] content) {
        UUID id = UUID.randomUUID();
        String sha256 = sha256(content);
        repository.insert(id, channel, fileName, sha256, content);
        events.publishEvent(new PaymentFileReceived(id, channel, fileName, sha256));
        return id;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
