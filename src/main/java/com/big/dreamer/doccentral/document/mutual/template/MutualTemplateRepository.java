package com.big.dreamer.doccentral.document.mutual.template;

import com.big.dreamer.doccentral.document.template.EditableTemplateRepository;
import com.big.dreamer.doccentral.document.DocumentGenerationException;
import com.big.dreamer.doccentral.storage.ApplicationDirectories;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MutualTemplateRepository extends EditableTemplateRepository {
    private static final String PREVIOUS_PAYMENT_DEFAULT = "document-templates/mutual/obsolete/payment-v3.txt";

    public MutualTemplateRepository(ApplicationDirectories directories) {
        super(directories.templatesDirectory("mutual"), "mutual");
    }

    @Override
    @PostConstruct
    public synchronized void initializeTemplates() {
        super.initializeTemplates();
        migrateDefault("payment.txt", previousPaymentDefault());
    }

    private String previousPaymentDefault() {
        try (InputStream stream = MutualTemplateRepository.class.getClassLoader()
                .getResourceAsStream(PREVIOUS_PAYMENT_DEFAULT)) {
            if (stream == null) {
                throw new DocumentGenerationException("No se encontró la plantilla anterior de pago.", null);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        } catch (IOException exception) {
            throw new DocumentGenerationException("No se pudo leer la plantilla anterior de pago.", exception);
        }
    }
}
