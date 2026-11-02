// ABOUTME: Unit tests for message format detection and hardened XSD validation of incoming files.
// ABOUTME: Includes attack payloads (XXE, entity expansion) that must be rejected without being resolved.
package dev.thinkahead.sepa.validation.xml;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XmlProcessingTest {

    private final FormatDetector detector = new FormatDetector();
    private final SchemaValidator schemaValidator = new SchemaValidator(1_000);

    @Test
    void detectsPain001Version09ByNamespace() {
        assertThat(detector.detect(bytes(VALID_PAIN_001))).contains(MessageFormat.PAIN_001_001_09);
    }

    @Test
    void detectsNothingForUnknownNamespace() {
        var xml = VALID_PAIN_001.replace("pain.001.001.09", "pain.001.001.99");

        assertThat(detector.detect(bytes(xml))).isEmpty();
    }

    @Test
    void detectsNothingForMalformedXml() {
        assertThat(detector.detect(bytes("<Document><unclosed>"))).isEmpty();
    }

    @Test
    void acceptsSchemaValidFile() {
        assertThat(schemaValidator.validate(MessageFormat.PAIN_001_001_09, bytes(VALID_PAIN_001)))
                .isEmpty();
    }

    @Test
    void reportsSchemaViolationsWithLineNumbers() {
        var xml = VALID_PAIN_001.replace("<MsgId>MSG-2026-10-31-001</MsgId>", "");

        assertThat(schemaValidator.validate(MessageFormat.PAIN_001_001_09, bytes(xml)))
                .isNotEmpty()
                .allSatisfy(error -> assertThat(error.line()).isPositive());
    }

    @Test
    void acceptsAValidFileAgainstTheDkSubset() {
        assertThat(schemaValidator.validateDkSubset(MessageFormat.PAIN_001_001_09, bytes(VALID_PAIN_001)))
                .isEmpty();
    }

    @Test
    void reportsContentOutsideSepaOnlyAgainstTheDkSubset() {
        var cheque = VALID_PAIN_001.replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>CHK</PmtMtd>");
        var chargesToDebtor = VALID_PAIN_001.replace("<ChrgBr>SLEV</ChrgBr>", "<ChrgBr>DEBT</ChrgBr>");

        assertThat(schemaValidator.validate(MessageFormat.PAIN_001_001_09, bytes(cheque)))
                .isEmpty();
        assertThat(schemaValidator.validate(MessageFormat.PAIN_001_001_09, bytes(chargesToDebtor)))
                .isEmpty();
        assertThat(schemaValidator.validateDkSubset(MessageFormat.PAIN_001_001_09, bytes(cheque)))
                .isNotEmpty()
                .allSatisfy(error -> assertThat(error.line()).isPositive());
        assertThat(schemaValidator.validateDkSubset(MessageFormat.PAIN_001_001_09, bytes(chargesToDebtor)))
                .isNotEmpty();
    }

    @Test
    void stopsSchemaValidationOnceTheErrorCapIsReached() {
        var xml = VALID_PAIN_001
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>XYZ</PmtMtd>")
                .replace("Ccy=\"EUR\"", "Ccy=\"eur\"")
                .replace("<ChrgBr>SLEV</ChrgBr>", "<ChrgBr>NONE</ChrgBr>");

        var errors = new SchemaValidator(2).validate(MessageFormat.PAIN_001_001_09, bytes(xml));

        assertThat(errors).hasSize(3);
        assertThat(errors.get(2).message()).contains("stopped after 2 errors");
    }

    @Test
    void rejectsExternalEntityWithoutResolvingIt() {
        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">&xxe;</Document>
                """;

        assertThat(schemaValidator.validate(MessageFormat.PAIN_001_001_09, bytes(xml)))
                .singleElement()
                .satisfies(error -> assertThat(error.message())
                        .containsIgnoringCase("DOCTYPE")
                        .doesNotContain("root:"));
    }

    @Test
    void rejectsEntityExpansionAttack() {
        var xml = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">&lol3;</Document>
                """;

        assertThat(schemaValidator.validate(MessageFormat.PAIN_001_001_09, bytes(xml)))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).containsIgnoringCase("DOCTYPE"));
    }

    @Test
    void formatDetectionDoesNotResolveExternalEntities() {
        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">&xxe;</Document>
                """;

        assertThat(detector.detect(bytes(xml))).isEmpty();
    }
}
