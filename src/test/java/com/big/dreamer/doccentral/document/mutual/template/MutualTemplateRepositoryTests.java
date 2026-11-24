package com.big.dreamer.doccentral.document.mutual.template;

import com.big.dreamer.doccentral.storage.ApplicationDirectories;
import com.big.dreamer.doccentral.document.template.EditableTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class MutualTemplateRepositoryTests {
    @TempDir Path directory;

    @Test
    void persistsCustomBlocksAcrossRestartAndValidatesExactVariables() {
        ApplicationDirectories directories = new ApplicationDirectories(directory.resolve("data").toString(), directory.resolve("docs").toString());
        MutualTemplateRepository repository = new MutualTemplateRepository(directories);
        repository.initializeTemplates();
        String original = repository.findAll().get("principal.txt");
        assertThatThrownBy(() -> repository.save("principal.txt", original.replace(":amount", ":amountExtra")))
                .hasMessageContaining("Faltan variables obligatorias: :amount");
        assertThatThrownBy(() -> repository.save("principal.txt", original + " :unknown"))
                .hasMessageContaining("Variables desconocidas");
        assertThatThrownBy(() -> repository.save("../principal.txt", original)).hasMessageContaining("desconocida");
        assertThatThrownBy(() -> repository.save("principal.txt", " ")).hasMessageContaining("vacía");
        repository.save("principal.txt", original + " Personalizado.");
        MutualTemplateRepository restarted = new MutualTemplateRepository(directories);
        restarted.initializeTemplates();
        assertThat(restarted.findAll().get("principal.txt")).endsWith("Personalizado.");
        assertThat(restarted.reset("principal.txt").content()).isEqualTo(original);
        assertThat(restarted.list()).allMatch(template -> template.usingDefault());
    }

    @Test
    void substitutesExactTokensInOnePassWithoutInterpretingUserValues() {
        assertThat(EditableTemplateRepository.render(":debtor / :debtorRole", Map.of("debtor", "$Ana :debtorRole", "debtorRole", "LA DEUDORA")))
                .isEqualTo("$Ana :debtorRole / LA DEUDORA");
    }

    @Test
    void migratesOnlyThePreviousPaymentDefault() throws Exception {
        ApplicationDirectories directories = new ApplicationDirectories(
                directory.resolve("data").toString(), directory.resolve("docs").toString());
        MutualTemplateRepository repository = new MutualTemplateRepository(directories);
        repository.initializeTemplates();
        String currentDefault = repository.findAll().get("payment.txt");
        String previousDefault = previousPaymentDefault();
        Path paymentTemplate = directories.templatesDirectory("mutual").resolve("payment.txt");

        Files.writeString(paymentTemplate, previousDefault, StandardCharsets.UTF_8);
        MutualTemplateRepository migrated = new MutualTemplateRepository(directories);
        migrated.initializeTemplates();
        assertThat(migrated.findAll().get("payment.txt")).isEqualTo(currentDefault);

        String customized = previousDefault + " Texto personalizado.";
        Files.writeString(paymentTemplate, customized, StandardCharsets.UTF_8);
        MutualTemplateRepository restarted = new MutualTemplateRepository(directories);
        restarted.initializeTemplates();
        assertThat(restarted.findAll().get("payment.txt")).isEqualTo(customized);
    }

    private String previousPaymentDefault() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "document-templates/mutual/obsolete/payment-v3.txt")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }

}
