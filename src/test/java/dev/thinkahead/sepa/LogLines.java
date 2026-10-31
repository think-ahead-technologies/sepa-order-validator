// ABOUTME: Test helper that parses captured structured (ECS JSON) log output into JSON nodes.
// ABOUTME: Lets tests assert on log fields such as message, level and the fileId from the MDC.
package dev.thinkahead.sepa;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.system.CapturedOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class LogLines {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private LogLines() {
    }

    /**
     * Every JSON log line in the captured output; other output (e.g. from before logging was set up) is skipped.
     */
    public static List<JsonNode> of(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> line.startsWith("{"))
                .map(JSON::readTree)
                .toList();
    }

    public static List<JsonNode> forFile(CapturedOutput output, UUID fileId) {
        return of(output).stream()
                .filter(line -> fileId.toString().equals(line.path("fileId").asString(null)))
                .toList();
    }

    public static List<String> messagesForFile(CapturedOutput output, UUID fileId) {
        return forFile(output, fileId).stream().map(line -> line.path("message").asString()).toList();
    }
}
