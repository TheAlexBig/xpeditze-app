package com.big.dreamer.doccentral.document.marriage.service;

import com.big.dreamer.doccentral.document.marriage.model.MarriageDocumentRequest;
import com.big.dreamer.doccentral.document.marriage.model.MarriageParent;
import com.big.dreamer.doccentral.document.marriage.model.MarriageParty;
import com.big.dreamer.doccentral.document.marriage.model.RecognizedChild;
import com.big.dreamer.doccentral.document.marriage.template.MarriageTemplateRepository;
import com.big.dreamer.doccentral.document.render.LegalDocumentRenderer;
import com.big.dreamer.doccentral.storage.ApplicationDirectories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.big.dreamer.doccentral.document.marriage.service.MarriageRulesServiceTests.*;
import static org.assertj.core.api.Assertions.assertThat;

class MarriageDocumentServiceTests {
    @TempDir Path directory;

    @Test
    void generatesPremaritalActMarriageInstrumentAndRegistrationControlInWordAndPdf() {
        MarriageTemplateRepository templates = new MarriageTemplateRepository(new ApplicationDirectories(
                directory.resolve("data").toString(), directory.resolve("documents").toString()));
        templates.initializeTemplates();
        MarriageDocumentService service = new MarriageDocumentService(
                templates, new MarriageRulesService(), new LegalDocumentRenderer());
        MarriageDocumentRequest request = request(
                party("Femenino", "SINGLE", LocalDate.of(1990, 1, 1)),
                party("Masculino", "SINGLE", LocalDate.of(1990, 1, 1)),
                witnesses(), details(null), List.of(
                        new RecognizedChild("HIJO UNO", "PARTIDA UNO"),
                        new RecognizedChild("HIJO DOS", "PARTIDA DOS")));

        assertThat(service.assemble(request).sections()).extracting(section -> section.text())
                .anySatisfy(text -> assertThat(text).contains(
                        "ACTA PREMATRIMONIAL", "COMUNIDAD DIFERIDA", "HIJO UNO", "HIJO DOS",
                        "MADRE, EMPLEADA, del domicilio de San Salvador, departamento de San Salvador"))
                .anySatisfy(text -> assertThat(text).contains(
                        "NÚMERO DOCE", "SÍ, QUIERO", "TESTIGO UNO", "HIJO UNO", "HIJO DOS"))
                .anySatisfy(text -> assertThat(text).contains("HOJA DE CONTROL", "quince días hábiles"));
        assertThat(service.assemble(request).sections().getFirst().signatures())
                .extracting(signature -> signature.title())
                .contains("NOTARIO AUTORIZANTE");
        assertThat(service.createDocument(request)).isNotEmpty();
        assertThat(service.createPdfDocument(request)).isNotEmpty();
    }

    @Test
    void rendersForeignParentDomicileWithoutSalvadoranDepartmentWording() {
        MarriageTemplateRepository templates = new MarriageTemplateRepository(new ApplicationDirectories(
                directory.resolve("data").toString(), directory.resolve("documents").toString()));
        templates.initializeTemplates();
        MarriageDocumentService service = new MarriageDocumentService(
                templates, new MarriageRulesService(), new LegalDocumentRenderer());
        MarriageParty original = party("Femenino", "SINGLE", LocalDate.of(1990, 1, 1));
        MarriageParty foreignParent = new MarriageParty(
                original.person(), original.birthDate(), original.familyStatus(), original.nationality(),
                original.birthPlace(), original.identityType(),
                new MarriageParent("MADRE", "EMPLEADA", "Guatemala", "", "Ciudad de Guatemala"),
                original.father(), original.birthCertificate(), original.familyStatusDocument(),
                original.currentMarriage(), original.canConsent(), original.prohibitedKinship(),
                original.prohibitedAdoptionRelationship(), original.spouseHomicideRestriction(),
                original.tutorRestriction(), original.speaksSpanish());

        MarriageDocumentRequest request = request(
                foreignParent,
                party("Masculino", "SINGLE", LocalDate.of(1990, 1, 1)),
                witnesses(), details(null), List.of());

        assertThat(service.assemble(request).sections()).extracting(section -> section.text())
                .anySatisfy(text -> assertThat(text).contains(
                        "MADRE, EMPLEADA, del domicilio de Ciudad de Guatemala, Guatemala"));
    }
}
