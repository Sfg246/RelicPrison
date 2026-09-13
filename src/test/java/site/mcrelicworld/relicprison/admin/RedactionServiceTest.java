package site.mcrelicworld.relicprison.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RedactionServiceTest {
    @Test
    void redactsFakeSecretsAcrossCommonFormats() {
        String fakeSecret = "sk-test_abcdefghijklmnopqrstuvwxyz";
        String content = String.join("\n",
                "password: hunter2",
                "\"apiKey\": \"" + fakeSecret + "\",",
                "db.url=jdbc:mysql://admin:swordfish@203.0.113.42:3306/prison?password=p455",
                "<token>xml-secret-value</token>",
                "Authorization: Bearer bearer-secret-value",
                "webhook = https://example.invalid/hook/secret",
                "plain suspicious 0123456789abcdef0123456789abcdef0123456789abcdef");

        RedactionService service = new RedactionService();
        String redacted = service.redactFile("config/fake.yml", content).content();
        String report = service.report().format();

        for (String value : new String[]{"hunter2", fakeSecret, "swordfish", "203.0.113.42",
                "p455", "xml-secret-value", "bearer-secret-value", "example.invalid/hook/secret"}) {
            assertFalse(redacted.contains(value), value);
            assertFalse(report.contains(value), value);
        }
        assertTrue(redacted.contains("<redacted>"));
        assertTrue(redacted.contains("<redacted-ip>"));
        assertTrue(report.contains("REDACT config/fake.yml:1"));
        assertTrue(report.contains("REVIEW config/fake.yml:7"));
    }

    @Test
    void inlineRedactionDoesNotExposeOriginalSecret() {
        String redacted = RedactionService.redactInline("Authorization: Bearer abcdefghijklmnopqrstuvwxyz");
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz"));
        assertTrue(redacted.contains("<redacted>"));
    }
}
