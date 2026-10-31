// ABOUTME: Unit tests for how schema validation treats validator warnings versus errors.
// ABOUTME: Warnings are logged by position only and never become violations, so they cannot reject a file.
package dev.thinkahead.sepa.validation.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.xml.sax.SAXParseException;

@ExtendWith(OutputCaptureExtension.class)
class SchemaWarningTest {

    private final List<SchemaError> errors = new ArrayList<>();
    private final SchemaValidator.CollectingErrorHandler handler =
            new SchemaValidator.CollectingErrorHandler(errors, 10);

    @Test
    void aWarningIsLoggedWithoutItsMessageAndNotCollectedAsAnError(CapturedOutput output) throws Exception {
        handler.warning(new SAXParseException("value 'DE89370400440532013000' of element 'IBAN'", null, null, 12, 7));

        assertThat(errors).isEmpty();
        assertThat(output).contains("Schema validation warning at line 12, column 7")
                .doesNotContain("DE89370400440532013000");
    }

    @Test
    void anErrorIsCollected() throws Exception {
        handler.error(new SAXParseException("probe error", null, null, 3, 4));

        assertThat(errors).containsExactly(new SchemaError(3, 4, "probe error"));
    }
}
