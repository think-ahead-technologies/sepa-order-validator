// ABOUTME: Unit tests for reading DK XML containers (DFÜ-Abkommen Anlage 3 V3.9, 9.1): detection, checks, splitting.
// ABOUTME: Embedded messages come out as standalone pain.001 documents; container-level faults are FF01.
package dev.thinkahead.sepa.validation.container;

import static dev.thinkahead.sepa.Fixtures.CONTAINER_HASH;
import static dev.thinkahead.sepa.Fixtures.CONTAINER_WITH_HASH;
import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.container;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.ElementText;
import dev.thinkahead.sepa.validation.pain001.Pain001Handler;
import dev.thinkahead.sepa.validation.pain001.Pain001Parser;
import dev.thinkahead.sepa.validation.pain001.PartyName;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import dev.thinkahead.sepa.validation.pain001.PaymentTypeInformation;
import dev.thinkahead.sepa.validation.pain001.PostalAddress;
import dev.thinkahead.sepa.validation.pain001.Totals;
import dev.thinkahead.sepa.validation.xml.FormatDetector;
import dev.thinkahead.sepa.validation.xml.MessageFormat;
import dev.thinkahead.sepa.validation.xml.SchemaValidator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContainerReaderTest {

    private final ContainerReader reader = new ContainerReader(1_000);

    private ContainerReading read(String xml) {
        byte[] content = bytes(xml);
        return reader.read(() -> new ByteArrayInputStream(content));
    }

    private static List<byte[]> messagesOf(ContainerReading reading) {
        assertThat(reading).isInstanceOf(ContainerReading.Split.class);
        return ((ContainerReading.Split) reading).messages();
    }

    private static List<Violation> faultsOf(ContainerReading reading) {
        assertThat(reading).isInstanceOf(ContainerReading.Fault.class);
        return ((ContainerReading.Fault) reading).violations();
    }

    @Test
    void recognisesAContainerByTheNamespaceOfItsRoot() {
        assertThat(reader.isContainer(bytes(container(validPain001("MSG-A"))))).isTrue();
        assertThat(reader.isContainer(bytes(VALID_PAIN_001))).isFalse();
        assertThat(reader.isContainer(bytes("<conxml><unclosed>"))).isFalse();
    }

    @Test
    void splitsEveryMessageIntoAStandaloneSchemaValidPain001Document() {
        List<byte[]> messages = messagesOf(read(container(validPain001("MSG-A"), validPain001("MSG-B"))));

        assertThat(messages).hasSize(2);
        assertThat(messages).allSatisfy(message -> {
            assertThat(new FormatDetector().detect(message)).contains(MessageFormat.PAIN_001_001_09);
            assertThat(new SchemaValidator(10).validate(MessageFormat.PAIN_001_001_09, message)).isEmpty();
            assertThat(new String(message, StandardCharsets.UTF_8)).startsWith("<Document ");
        });
        assertThat(messages).extracting(ContainerReaderTest::messageId).containsExactly("MSG-A", "MSG-B");
    }

    @Test
    void writesEachMessageInTheCanonicalFormItsContainerHashIsComputedOver() {
        List<byte[]> messages = messagesOf(read(CONTAINER_WITH_HASH));

        assertThat(messages).hasSize(2);
        assertThat(HexFormat.of().withUpperCase().formatHex(sha256(messages.getFirst()))).isEqualTo(CONTAINER_HASH);
    }

    @Test
    void acceptsAHashValueInLowerCase() {
        String lowerCase = CONTAINER_WITH_HASH.replace(CONTAINER_HASH, CONTAINER_HASH.toLowerCase());

        assertThat(messagesOf(read(lowerCase))).hasSize(2);
    }

    @Test
    void rejectsAContainerWhoseMessageDoesNotMatchItsHashWithFf01() {
        String tampered = CONTAINER_WITH_HASH.replaceFirst("<Nm>Lieferant AG</Nm>", "<Nm>Lieferant GmbH</Nm>");

        assertThat(faultsOf(read(tampered)))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.reasonCode()).isEqualTo("FF01");
                    assertThat(violation.path()).isEqualTo("MsgPain001[1]/HashValue");
                    assertThat(violation.message()).contains("does not match");
                });
    }

    @Test
    void keepsTheTextOfEachMessageIncludingUmlautsAndEscapedCharacters() {
        String message = validPain001("MSG-A").replace("<Nm>Lieferant AG</Nm>", "<Nm>Lieferant &amp; Söhne</Nm>");

        byte[] split = messagesOf(read(container(message))).getFirst();

        assertThat(texts(split))
                .contains(new ElementText("PmtInf[1]/CdtTrfTxInf[1]/Cdtr/Nm", "Lieferant & Söhne"))
                .contains(new ElementText("PmtInf[1]/Dbtr/PstlAdr/StrtNm", "Hauptstraße"));
    }

    @Test
    void declaresNamespacesThatTheMessageInheritsFromTheContainer() {
        String message = validPain001("MSG-A").replace(
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\">",
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\" "
                        + "xsi:schemaLocation=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09 pain.001.001.09.xsd\">");

        byte[] split = messagesOf(read(container(message))).getFirst();

        assertThat(new String(split, StandardCharsets.UTF_8))
                .contains("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        assertThat(new FormatDetector().detect(split)).contains(MessageFormat.PAIN_001_001_09);
    }

    @Test
    void leavesTheContentOfEmbeddedMessagesToTheirOwnValidation() {
        String schemaInvalid = validPain001("MSG-A").replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>XYZ</PmtMtd>");

        assertThat(messagesOf(read(container(schemaInvalid)))).hasSize(1);
    }

    @Test
    void rejectsASchemaInvalidContainerWithFf01() {
        String withoutContainerId = container(validPain001("MSG-A"))
                .replaceFirst("(?s)<ContainerId>.*?</ContainerId>", "");

        assertThat(faultsOf(read(withoutContainerId)))
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.reasonCode()).isEqualTo("FF01"));
    }

    @Test
    void rejectsAContainerOfDirectDebitsWithFf01() {
        String directDebits = container(validPain001("MSG-A"))
                .replace("MsgPain001", "MsgPain008")
                .replace("pain.001.001.09", "pain.008.001.08");

        assertThat(faultsOf(read(directDebits)))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.reasonCode()).isEqualTo("FF01");
                    assertThat(violation.message()).contains("MsgPain001");
                });
    }

    @Test
    void rejectsAnEmbeddedMessageWithANamespacePrefixWithFf01() {
        String prefixed = container(validPain001("MSG-A")
                .replace("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\">",
                        "<p:Document xmlns:p=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\" "
                                + "xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\">")
                .replace("</Document>", "</p:Document>"));

        assertThat(faultsOf(read(prefixed)))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.reasonCode()).isEqualTo("FF01");
                    assertThat(violation.message()).contains("prefix");
                });
    }

    @Test
    void rejectsAContainerNotEncodedInUtf8WithFf01() {
        String latin1 = container(validPain001("MSG-A")).replace("encoding=\"UTF-8\"", "encoding=\"ISO-8859-1\"");

        assertThat(faultsOf(reader.read(() -> new ByteArrayInputStream(latin1.getBytes(StandardCharsets.ISO_8859_1)))))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.reasonCode()).isEqualTo("FF01");
                    assertThat(violation.message()).contains("UTF-8");
                });
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String messageId(byte[] message) {
        return new Pain001Parser().parse(message, new TextCollector()).messageId();
    }

    private static List<ElementText> texts(byte[] message) {
        var collector = new TextCollector();
        new Pain001Parser().parse(message, collector);
        return collector.texts;
    }

    /**
     * Collects the element texts of a parsed message and ignores everything else.
     */
    private static final class TextCollector implements Pain001Handler {

        final List<ElementText> texts = new ArrayList<>();

        @Override
        public void paymentInformation(PaymentInformation paymentInformation) {
        }

        @Override
        public void transaction(CreditTransferTransaction transaction) {
        }

        @Override
        public void totals(Totals totals) {
        }

        @Override
        public void partyName(PartyName partyName) {
        }

        @Override
        public void postalAddress(PostalAddress postalAddress) {
        }

        @Override
        public void elementText(ElementText elementText) {
            texts.add(elementText);
        }

        @Override
        public void paymentTypeInformation(PaymentTypeInformation paymentTypeInformation) {
        }
    }
}
