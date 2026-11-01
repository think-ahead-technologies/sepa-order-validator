// ABOUTME: End-to-end tests of the walking skeleton: a customer drops a file on SFTP and receives a pain.002.
// ABOUTME: Runs the complete application against real PostgreSQL and SFTP containers, no test doubles.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class PaymentFileProcessingEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String STS = "/*[local-name()='Document']/*[local-name()='CstmrPmtStsRpt']";
    private static final String GROUP = STS + "/*[local-name()='OrgnlGrpInfAndSts']";

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Test
    void validFileIsAcceptedAndReportedWithAccp() throws Exception {
        String messageId = uniqueMessageId();
        Delivery delivery = submit(validPain001(messageId));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo(messageId);
        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='OrgnlMsgNmId']")).isEqualTo("pain.001.001.09");
        assertThat(reasonCodes(delivery.report())).isEmpty();
        assertThat(fileStatus(delivery.fileName())).isEqualTo("ACCEPTED");
        assertThat(jdbc.sql("select replied_at is not null from payment_file where file_name = ?")
                .param(delivery.fileName()).query(Boolean.class).single()).isTrue();
        assertThat(customer.list("inbound")).doesNotContain(delivery.fileName());
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void invalidFileIsRejectedAsAWholeWithEveryReason(CapturedOutput output) throws Exception {
        Delivery delivery = submit(validPain001(uniqueMessageId())
                .replace("NL91ABNA0417164300", "NL91ABNA0417164301")
                .replace("Ccy=\"EUR\">50.50", "Ccy=\"USD\">50.50"));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactlyInAnyOrder("AC03", "AM03");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(delivery.fileName());
        UUID fileId = fileId(delivery.fileName());
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(LogLines.messagesForFile(output, fileId))
                .anySatisfy(message -> assertThat(message).startsWith("Received payment file " + delivery.fileName()))
                .anySatisfy(message -> assertThat(message).startsWith("Validated payment file: rejected"))
                .anySatisfy(message -> assertThat(message).startsWith("Delivered status report")));
        assertThat(output).doesNotContain("NL91ABNA0417164301");
    }

    @Test
    void ibansAreMaskedInTheLogOutput(CapturedOutput output) {
        LoggerFactory.getLogger(PaymentFileProcessingEndToEndTest.class)
                .info("Creditor account DE89370400440532013000");

        assertThat(LogLines.of(output)).anySatisfy(line ->
                assertThat(line.path("message").asString()).isEqualTo("Creditor account DE89**************3000"));
        assertThat(output).doesNotContain("DE89370400440532013000");
    }

    @Test
    void paymentToACountryThatJoinedSepaIn2025IsAccepted() throws Exception {
        Delivery delivery = submit(validPain001(uniqueMessageId())
                .replace("NL91ABNA0417164300", "MK07250120000058984"));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("ACCEPTED");
    }

    @Test
    void lowerCaseIbanIsRejectedWithAc03RatherThanAsSchemaError() throws Exception {
        Delivery delivery = submit(validPain001(uniqueMessageId())
                .replace("NL91ABNA0417164300", "NL91abna0417164300"));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactly("AC03");
    }

    @Test
    void sepaFieldRuleViolationsAreReportedWithTheirReasonCodes() throws Exception {
        Delivery delivery = submit(Fixtures.withSepaFieldRuleViolations(uniqueMessageId()));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactlyInAnyOrder("AM01", "RC01", "AM12", "FF01", "FF01");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void unstructuredAddressAndMissingCreditorNameAreRejectedWithRr03() throws Exception {
        Delivery delivery = submit(Fixtures.withCreditorNameAndAddressViolations(uniqueMessageId()));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactly("RR03", "RR03");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void pastDatedSepaInstantWithAnUnstructuredAddressIsRejectedWithRr03() throws Exception {
        java.time.LocalDate pastSunday = java.time.LocalDate.of(2026, 10, 25);

        Delivery delivery = submit(Fixtures.pastDatedInstantWithUnstructuredCreditorAddress(uniqueMessageId(),
                pastSunday));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactly("RR03");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void charactersOutsideTheDkCharacterSetAreRejectedWithCh16() throws Exception {
        Delivery delivery = submit(Fixtures.withCharacterSetViolations(uniqueMessageId()));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactly("CH16", "CH16");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void sepaInstantWithAPastSundayAsEarliestExecutionIsAccepted() throws Exception {
        java.time.LocalDate pastSunday = java.time.LocalDate.of(2026, 10, 25);

        Delivery delivery = submit(Fixtures.validInstantPain001(uniqueMessageId(), pastSunday));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(reasonCodes(delivery.report())).isEmpty();
        assertThat(fileStatus(delivery.fileName())).isEqualTo("ACCEPTED");
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void sepaInstantInboxAcceptsSepaInstantAndRejectsSepaCreditTransfersWithAg02() throws Exception {
        Delivery instant = submit("inbound-instant",
                Fixtures.validInstantPain001(uniqueMessageId(), Fixtures.futureExecutionDate()));
        Delivery creditTransfer = submit("inbound-instant", validPain001(uniqueMessageId()));

        assertThat(xpath(instant.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(xpath(creditTransfer.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(creditTransfer.report())).containsExactly("AG02");
        assertAuditTrailComplete(instant.fileName());
    }

    @Test
    void ordersOutsideSepaCreditTransferAreRejected() throws Exception {
        Delivery cheque = submit(validPain001(uniqueMessageId())
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>CHK</PmtMtd>"));
        Delivery chargesToDebtor = submit(validPain001(uniqueMessageId())
                .replace("<ChrgBr>SLEV</ChrgBr>", "<ChrgBr>DEBT</ChrgBr>"));
        Delivery urgentPayment = submit(validPain001(uniqueMessageId())
                .replaceFirst("(<SvcLvl>\\s*<Cd>)SEPA(</Cd>)", "$1URGP$2"));

        for (Delivery delivery : List.of(cheque, chargesToDebtor, urgentPayment)) {
            assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
            assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
            assertAuditTrailComplete(delivery.fileName());
        }
        assertThat(reasonCodes(cheque.report())).containsOnly("FF01");
        assertThat(reasonCodes(chargesToDebtor.report())).containsOnly("FF01");
        assertThat(reasonCodes(urgentPayment.report())).containsExactly("AG02");
    }

    @Test
    void executionOnAWeekendIsRejectedWithDt01() throws Exception {
        java.time.LocalDate saturday = Fixtures.futureExecutionDate()
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));

        Delivery delivery = submit(validPain001(uniqueMessageId(), saturday));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactly("DT01");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(delivery.fileName());
    }

    @Test
    void eachMessageOfAContainerIsAnsweredWithItsOwnStatusReport() throws Exception {
        String acceptedMessageId = uniqueMessageId();
        String rejectedMessageId = uniqueMessageId();
        String fileName = "e2e-container-" + UUID.randomUUID() + ".xml";
        String baseName = fileName.replace(".xml", "");
        customer.upload("inbound", fileName, bytes(Fixtures.container(validPain001(acceptedMessageId),
                validPain001(rejectedMessageId).replace("NL91ABNA0417164300", "NL91ABNA0417164301"))));

        List<String> reports = await().atMost(TIMEOUT).until(
                () -> customer.list("outbound").stream().filter(name -> name.startsWith(baseName + "-")).sorted()
                        .toList(),
                names -> names.size() == 2);

        assertThat(reports).allSatisfy(name -> assertThat(name).startsWith(baseName + "-msg"));
        byte[] first = customer.download("outbound", reports.get(0));
        byte[] second = customer.download("outbound", reports.get(1));
        assertPain002SchemaValid(first);
        assertPain002SchemaValid(second);
        assertThat(reports.get(0)).startsWith(baseName + "-msg1-");
        assertThat(xpath(first, GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo(acceptedMessageId);
        assertThat(xpath(first, GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(xpath(second, GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo(rejectedMessageId);
        assertThat(xpath(second, GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(second)).containsExactly("AC03");
        assertThat(fileStatus(fileName)).isEqualTo("SPLIT");
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() ->
                assertThat(customer.list("outbound")).filteredOn(name -> name.startsWith(baseName + "-"))
                        .as("no status report for the container itself").hasSize(2));
    }

    @Test
    void aContainerWhoseHashMatchesIsSplitAndEachMessageAccepted() throws Exception {
        String fileName = "e2e-hashed-container-" + UUID.randomUUID() + ".xml";
        String baseName = fileName.replace(".xml", "");
        customer.upload("inbound", fileName, bytes(Fixtures.CONTAINER_WITH_HASH));

        List<String> reports = await().atMost(TIMEOUT).until(
                () -> customer.list("outbound").stream().filter(name -> name.startsWith(baseName + "-msg")).sorted()
                        .toList(),
                names -> names.size() == 2);

        for (int i = 0; i < 2; i++) {
            byte[] report = customer.download("outbound", reports.get(i));
            assertPain002SchemaValid(report);
            assertThat(xpath(report, GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo("MSG-CONTAINER-HASH-" + (i + 1));
            assertThat(xpath(report, GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        }
        assertThat(fileStatus(fileName)).isEqualTo("SPLIT");
    }

    @Test
    void aBrokenContainerIsRejectedAsAWholeWithOneStatusReport() throws Exception {
        Delivery delivery = submit(Fixtures.container(validPain001(uniqueMessageId()))
                .replaceFirst("(?s)<ContainerId>.*?</ContainerId>", ""));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo("NOTPROVIDED");
        assertThat(reasonCodes(delivery.report())).isNotEmpty().containsOnly("FF01");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
    }

    @Test
    void schemaInvalidFileIsRejectedWithFf01() throws Exception {
        Delivery delivery = submit(validPain001(uniqueMessageId())
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>XYZ</PmtMtd>"));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).isNotEmpty().containsOnly("FF01");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
    }

    @Test
    void resubmittedFileIsRejectedAsDuplicateMessage() throws Exception {
        String xml = validPain001(uniqueMessageId());
        Delivery original = submit(xml);

        Delivery resubmission = submit(xml);

        assertThat(xpath(original.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(xpath(resubmission.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(resubmission.report())).containsExactly("DU01");
        assertThat(fileStatus(original.fileName())).isEqualTo("ACCEPTED");
        assertThat(fileStatus(resubmission.fileName())).isEqualTo("REJECTED");
        assertAuditTrailComplete(resubmission.fileName());
    }

    @Test
    void slowlyUploadedFileIsValidatedOnlyOnceComplete() throws Exception {
        String messageId = uniqueMessageId();
        String fileName = "slow-e2e-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";

        customer.uploadSlowly("inbound", fileName, bytes(validPain001(messageId)), 20, Duration.ofMillis(100),
                () -> assertThat(customer.list("outbound")).noneMatch(name -> name.startsWith(reportPrefix)));

        String reportName = await().atMost(TIMEOUT).until(
                () -> customer.list("outbound").stream().filter(name -> name.startsWith(reportPrefix))
                        .findFirst().orElse(null),
                name -> name != null);
        byte[] report = customer.download("outbound", reportName);
        assertThat(xpath(report, GROUP + "/*[local-name()='GrpSts']")).isEqualTo("ACCP");
        assertThat(xpath(report, GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo(messageId);
        assertThat(fileStatus(fileName)).isEqualTo("ACCEPTED");
    }

    @Test
    void fileLeftInProcessingByACrashedInstanceIsReportedExactlyOnce() {
        String fileName = "left-behind-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";

        customer.upload("processing", UUID.randomUUID() + "-" + fileName, bytes(validPain001(uniqueMessageId())));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound"))
                .filteredOn(name -> name.startsWith(reportPrefix)).hasSize(1));
        assertThat(fileStatus(fileName)).isEqualTo("ACCEPTED");
        assertAuditTrailComplete(fileName);
    }

    @Test
    void fileAboveTheMaximumSizeIsRejectedWithFf01() throws Exception {
        Delivery delivery = submit(Fixtures.paddedTo(validPain001(uniqueMessageId()), 1_100_000));

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(reasonCodes(delivery.report())).containsExactly("FF01");
        assertThat(fileStatus(delivery.fileName())).isEqualTo("REJECTED");
    }

    @Test
    void reportedReasonsAreCappedForAFileWithVeryManyErrors() throws Exception {
        Delivery delivery = submit(Fixtures.withInvalidCreditorIbans(uniqueMessageId(), 1_200));

        List<String> reasons = reasonCodes(delivery.report());
        assertThat(reasons).hasSize(1_001);
        assertThat(reasons.subList(0, 1_000)).containsOnly("AC03");
        assertThat(reasons.get(1_000)).isEqualTo("NARR");
        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='StsRsnInf'][1001]/*[local-name()='AddtlInf'][2]"))
                .contains("200 more violations");
    }

    @Test
    void xmlAttackFileIsRejectedWithoutResolvingEntities() throws Exception {
        Delivery delivery = submit("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">&xxe;</Document>
                """);

        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='GrpSts']")).isEqualTo("RJCT");
        assertThat(xpath(delivery.report(), GROUP + "/*[local-name()='OrgnlMsgId']")).isEqualTo("NOTPROVIDED");
        assertThat(reasonCodes(delivery.report())).containsExactly("FF01");
        assertThat(new String(delivery.report())).doesNotContain("root:");
    }

    private record Delivery(String fileName, byte[] report) {
    }

    private Delivery submit(String xml) {
        return submit("inbound", xml);
    }

    private Delivery submit(String inbox, String xml) {
        String fileName = "e2e-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";
        customer.upload(inbox, fileName, bytes(xml));

        String reportName = await().atMost(TIMEOUT).until(
                () -> customer.list("outbound").stream().filter(name -> name.startsWith(reportPrefix))
                        .findFirst().orElse(null),
                name -> name != null);
        byte[] report = customer.download("outbound", reportName);
        assertPain002SchemaValid(report);
        return new Delivery(fileName, report);
    }

    private UUID fileId(String fileName) {
        return jdbc.sql("select id from payment_file where file_name = ?").param(fileName)
                .query(UUID.class).single();
    }

    private String fileStatus(String fileName) {
        return jdbc.sql("select status from payment_file where file_name = ?").param(fileName)
                .query(String.class).single();
    }

    private void assertAuditTrailComplete(String fileName) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.sql("""
                        select a.event_type from audit_event a
                        join payment_file f on f.id = a.file_id
                        where f.file_name = ?""").param(fileName).query(String.class).list())
                .containsExactlyInAnyOrder("PaymentFileReceived", "PaymentFileValidated", "StatusReportDelivered"));
    }

    private static void assertPain002SchemaValid(byte[] report) {
        try {
            SchemaFactory.newDefaultInstance()
                    .newSchema(PaymentFileProcessingEndToEndTest.class.getResource("/xsd/pain.002.001.10.xsd"))
                    .newValidator()
                    .validate(new StreamSource(new ByteArrayInputStream(report)));
        } catch (Exception e) {
            throw new AssertionError("Delivered pain.002 is not schema-valid", e);
        }
    }

    private static List<String> reasonCodes(byte[] report) throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newDefaultInstance().newXPath().evaluate(
                GROUP + "/*[local-name()='StsRsnInf']/*[local-name()='Rsn']/*[local-name()='Cd']",
                document(report), XPathConstants.NODESET);
        return java.util.stream.IntStream.range(0, nodes.getLength())
                .mapToObj(i -> nodes.item(i).getTextContent()).toList();
    }

    private static String xpath(byte[] report, String expression) throws Exception {
        return XPathFactory.newDefaultInstance().newXPath().evaluate(expression, document(report));
    }

    private static Document document(byte[] report) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(report));
    }
}
