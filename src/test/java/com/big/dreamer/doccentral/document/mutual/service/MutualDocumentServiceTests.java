package com.big.dreamer.doccentral.document.mutual.service;

import com.big.dreamer.doccentral.document.carsale.model.LegalAgentDetails;
import com.big.dreamer.doccentral.document.carsale.model.CarDetails;
import com.big.dreamer.doccentral.document.carsale.model.PersonDetails;
import com.big.dreamer.doccentral.document.carsale.model.CompanyDetails;
import com.big.dreamer.doccentral.document.mutual.model.MutualDocumentRequest;
import com.big.dreamer.doccentral.document.mutual.model.MutualTerms;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.big.dreamer.doccentral.document.mutual.model.MutualInstrumentType;
import com.big.dreamer.doccentral.document.mutual.model.MutualGuaranteeType;
import com.big.dreamer.doccentral.document.mutual.model.MutualTermMode;
import com.big.dreamer.doccentral.document.mutual.model.MutualTermUnit;
import com.big.dreamer.doccentral.document.mutual.model.MutualVehiclePledge;
import com.big.dreamer.doccentral.document.mutual.model.MutualInterestPeriod;
import com.big.dreamer.doccentral.document.mutual.model.MutualPaymentMethod;
import com.big.dreamer.doccentral.document.mutual.model.MutualPaymentScheduleMode;
import com.big.dreamer.doccentral.storage.ApplicationDirectories;
import com.big.dreamer.doccentral.document.mutual.template.MutualTemplateRepository;
import com.big.dreamer.doccentral.document.render.LegalDocumentRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MutualDocumentServiceTests {

    @TempDir
    Path directory;
    private MutualTemplateRepository repository;
    private MutualDocumentService service;

    @BeforeEach
    void setUp() {
        ApplicationDirectories directories = new ApplicationDirectories(directory.resolve("data").toString(), directory.resolve("documents").toString());
        directories.initialize();
        repository = new MutualTemplateRepository(directories);
        repository.initializeTemplates();
        service = new MutualDocumentService(repository,
                new MutualRulesService(new MutualFinancialCalculator()), new LegalDocumentRenderer());
    }

    @Test
    void customBlocksApplyToWordAndPdfAndResetToOriginal() throws Exception {
        String original = repository.findAll().get("purpose.txt");
        repository.save("purpose.txt", original + " Texto personalizado del destino.");
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(service.createDocument(request(true))));
             var pdf = Loader.loadPDF(service.createPdfDocument(request(true)))) {
            assertThat(word.getParagraphs().stream().map(p -> p.getText()).reduce("", String::concat))
                    .contains("Texto personalizado del destino.").doesNotContain(":fundsPurpose");
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+", " "))
                    .contains("Texto personalizado del destino.").doesNotContain(":fundsPurpose");
        }
        repository.reset("purpose.txt");
        assertThat(service.assemble(request(true)).contract()).doesNotContain("Texto personalizado");
    }

    @Test
    void omitsOptionalBlocksAndKeepsSequentialNumbering() {
        MutualDocumentRequest base = request(false);
        MutualTerms t = base.terms();
        MutualTerms terms = new MutualTerms(t.amount(), t.term(), t.dueDate(), t.installmentCount(),
                t.installmentAmount(), t.paymentBank(), t.paymentAccount(), "", "", t.fundsPurpose(),
                false, "", "", t.specialDomicile(), t.signingPlace(), t.signingState(), t.signingDate(),
                t.signingTime(), t.identifiesDebtor(), t.identifiesCreditor());
        String text = service.assemble(new MutualDocumentRequest(base.debtor(), base.creditor(), terms, base.legalAgent())).contract();
        assertThat(text).doesNotContain("INTERESES:", "GARANTÍA:", "gastos administrativos")
                .contains("III) ORIGEN", "IV) CAUSALES", "V) CADUCIDAD", "VI) DOMICILIO");
    }

    @Test
    void createsMutualWithOptionalInterestAndGuaranteeClauses() throws Exception {
        MutualDocumentRequest request = request(true);

        byte[] bytes = service.createDocument(request);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + right);
            assertThat(text)
                    .contains("CONTRATO DE MUTUO")
                    .contains("me denominaré \"LA DEUDORA\"")
                    .contains("me denominaré \"EL ACREEDOR\"")
                    .contains("INTERESES")
                    .contains("GARANTÍA")
                    .contains("letra de cambio")
                    .contains("SETECIENTOS CINCUENTA DÓLARES CON VEINTIDÓS CENTAVOS DE DÓLAR")
                    .contains("LA DEUDORA")
                    .contains("EL ACREEDOR")
                    .contains("a quien no conozco e identifico con Documento Único de Identidad homologado número CERO TRES-UNO")
                    .contains("a quien no conozco e identifico con Documento Único de Identidad homologado número CERO CUATRO-DOS")
                    .doesNotContain("número CERO TRES-UNO, a quien");
        }
    }

    @Test
    void rejectsSamePersonAsDebtorAndCreditor() {
        MutualDocumentRequest valid = request(false);
        MutualDocumentRequest invalid = new MutualDocumentRequest(
                valid.debtor(), valid.debtor(), valid.terms(), valid.legalAgent());

        assertThatThrownBy(() -> MutualRequestValidator.validate(invalid))
                .hasMessageContaining("personas diferentes");
    }

    @Test
    void createsMutualAsPdf() {
        assertThat(service.createPdfDocument(request(true))).isNotEmpty();
    }

    @Test
    void placesKnowledgeBeforeIdentificationForBothAnswers() {
        MutualDocumentRequest base = request(false);
        MutualTerms t = base.terms();
        MutualTerms terms = new MutualTerms(t.amount(), t.term(), t.dueDate(), t.installmentCount(),
                t.installmentAmount(), t.paymentBank(), t.paymentAccount(), t.monthlyInterest(),
                t.defaultInterest(), t.fundsPurpose(), false, "", t.administrativeExpenses(),
                t.specialDomicile(), t.signingPlace(), t.signingState(), t.signingDate(),
                t.signingTime(), "Sí", "No");

        String authentic = service.assemble(new MutualDocumentRequest(
                base.debtor(), base.creditor(), terms, base.legalAgent())).authentic();

        assertThat(authentic)
                .contains("a quien conozco e identifico con Documento Único de Identidad homologado número CERO TRES-UNO")
                .contains("a quien no conozco e identifico con Documento Único de Identidad homologado número CERO CUATRO-DOS")
                .doesNotContain("número CERO TRES-UNO, a quien", "número CERO CUATRO-DOS, a quien");
    }

    @Test
    void publicDeedUsesProtocolOpeningAndOmitsSeparateAuthentic() {
        MutualDocumentRequest base = request(false);
        MutualTerms t = base.terms();
        MutualTerms terms = new MutualTerms(t.amount(), "DOCE MESES", t.dueDate(), t.installmentCount(),
                t.installmentAmount(), t.paymentBank(), t.paymentAccount(), t.monthlyInterest(),
                t.defaultInterest(), t.fundsPurpose(), false, "", t.administrativeExpenses(), "",
                t.signingPlace(), t.signingState(), t.signingDate(), t.signingTime(),
                t.identifiesDebtor(), t.identifiesCreditor(), new BigDecimal("750.22"),
                MutualTermMode.DURATION, 12, MutualTermUnit.MONTHS, LocalDate.of(2026, 4, 9),
                null, 6, new BigDecimal("3.75"), MutualInstrumentType.PUBLIC_DEED,
                MutualGuaranteeType.NONE, 1, "UNO", "");
        MutualDocumentContent content = service.assemble(new MutualDocumentRequest(
                base.debtor(), base.creditor(), null, terms, base.legalAgent()));
        assertThat(content.contract()).startsWith("NÚMERO UNO")
                .contains("PLAN DE PAGOS", "Capital SETECIENTOS", "intereses", "total a pagar",
                        "La primera cuota vencerá", "las cuotas sucesivas vencerán",
                        "hasta la última, que vencerá",
                        "Las cuotas pactadas ya comprenden el interés ordinario",
                        "no se agregará otro interés ordinario")
                .doesNotContain("Vencimientos:", "1)", "2)", "$", "/2026", "/2027");
        assertThat(content.authentic()).isEmpty();
    }

    @Test
    void rendersVehiclePledgeWithStructuredVehicleData() {
        MutualDocumentRequest base = request(false);
        MutualTerms t = base.terms();
        MutualTerms terms = new MutualTerms(t.amount(), "SEIS MESES", t.dueDate(), t.installmentCount(),
                t.installmentAmount(), t.paymentBank(), t.paymentAccount(), "", "DIEZ", t.fundsPurpose(),
                false, "", "", "", t.signingPlace(), t.signingState(), t.signingDate(), t.signingTime(),
                t.identifiesDebtor(), t.identifiesCreditor(), new BigDecimal("750.22"),
                MutualTermMode.DURATION, 6, MutualTermUnit.MONTHS, LocalDate.of(2026, 4, 9),
                null, 2, BigDecimal.ZERO, MutualInstrumentType.PRIVATE_AUTHENTICATED,
                MutualGuaranteeType.VEHICLE_PLEDGE, null, "", "");
        CarDetails vehicle = new CarDetails("P DOS NUEVE UNO CUATRO", "KIA", "SOUL", "BLANCO",
                "DOS MIL CATORCE", "CINCO ASIENTOS", "PROPIEDAD", "AUTOMÓVIL", "COMPACTO",
                "MOTOR UNO", "CHASIS UNO", "VIN UNO");
        MutualDocumentContent content = service.assemble(new MutualDocumentRequest(
                base.debtor(), base.creditor(), null,
                new MutualVehiclePledge(vehicle, "SETECIENTOS CINCUENTA DÓLARES"),
                terms, base.legalAgent()));

        assertThat(content.contract()).contains("CONTRATO DE MUTUO CON GARANTÍA PRENDARIA",
                "GARANTÍA PRENDARIA", "prenda sin desplazamiento",
                "P DOS NUEVE UNO CUATRO", "Registro de Garantías Mobiliarias");
    }

    @Test
    void rendersCorporateCreditorCashPaymentAndAnnualDefaultInterest() {
        MutualDocumentRequest base = request(false);
        CompanyDetails company = new CompanyDetails(
                "CRÉDITO EJEMPLO, SOCIEDAD ANÓNIMA DE CAPITAL VARIABLE",
                "CRÉDITO EJEMPLO", "SALVADOREÑA", "SAN SALVADOR",
                "Distrito de San Salvador", "CERO SEIS UNO CUATRO",
                "ADMINISTRADOR ÚNICO POR SIETE AÑOS", "CREDENCIAL INSCRITA AL NÚMERO VEINTICINCO");
        PersonDetails representative = new PersonDetails(
                "Luis Arístides", "Díaz Madrid", "La Libertad",
                "Santa Tecla", "CERO CUATRO-DOS", "Masculino",
                "TREINTA Y SEIS", "Empresario", company);
        MutualTerms terms = withEarlyPayments(withPayment(base.terms(), MutualPaymentMethod.CASH,
                "LAS OFICINAS DE LA SOCIEDAD ACREEDORA", MutualInterestPeriod.ANNUAL));

        MutualDocumentContent content = service.assemble(new MutualDocumentRequest(
                base.debtor(), representative, terms, base.legalAgent()));

        assertThat(content.contract())
                .contains("LA SOCIEDAD ACREEDORA", "CRÉDITO EJEMPLO",
                        "actuando en mi calidad de ADMINISTRADOR ÚNICO",
                        "personería que acredito mediante CREDENCIAL",
                        "El pago se realizará en efectivo en LAS OFICINAS",
                        "UNO por ciento anual", "ABONOS ANTICIPADOS",
                        "directamente al capital sin penalización");
        assertThat(content.authentic())
                .contains("actuando en su calidad de ADMINISTRADOR ÚNICO",
                        "personería que acredita mediante CREDENCIAL");
    }

    private MutualTerms withPayment(MutualTerms terms, MutualPaymentMethod method,
                                    String details, MutualInterestPeriod period) {
        return new MutualTerms(terms.amount(), terms.term(), terms.dueDate(), terms.installmentCount(),
                terms.installmentAmount(), terms.paymentBank(), terms.paymentAccount(),
                terms.monthlyInterest(), terms.defaultInterest(), terms.fundsPurpose(),
                terms.billOfExchangeGuarantee(), terms.guaranteeDueDate(), terms.administrativeExpenses(),
                terms.specialDomicile(), terms.signingPlace(), terms.signingState(), terms.signingDate(),
                terms.signingTime(), terms.identifiesDebtor(), terms.identifiesCreditor(),
                terms.numericAmount(), terms.termMode(), terms.termQuantity(), terms.termUnit(),
                terms.startDate(), terms.dueDateIso(), terms.numericInstallmentCount(),
                terms.numericMonthlyInterest(), terms.instrumentType(), terms.guaranteeType(),
                terms.deedNumber(), terms.deedNumberText(), terms.guaranteeDetails(), method, details, period);
    }

    private MutualTerms withEarlyPayments(MutualTerms terms) {
        return new MutualTerms(terms.amount(), terms.term(), terms.dueDate(), terms.installmentCount(),
                terms.installmentAmount(), terms.paymentBank(), terms.paymentAccount(),
                terms.monthlyInterest(), terms.defaultInterest(), terms.fundsPurpose(),
                terms.billOfExchangeGuarantee(), terms.guaranteeDueDate(), terms.administrativeExpenses(),
                terms.specialDomicile(), terms.signingPlace(), terms.signingState(), terms.signingDate(),
                terms.signingTime(), terms.identifiesDebtor(), terms.identifiesCreditor(),
                terms.numericAmount(), terms.termMode(), terms.termQuantity(), terms.termUnit(),
                terms.startDate(), terms.dueDateIso(), terms.numericInstallmentCount(),
                terms.numericMonthlyInterest(), terms.instrumentType(), terms.guaranteeType(),
                terms.deedNumber(), terms.deedNumberText(), terms.guaranteeDetails(),
                terms.paymentMethod(), terms.paymentDetails(), terms.defaultInterestPeriod(),
                MutualPaymentScheduleMode.CALCULATED, java.util.List.of(), true,
                "Los abonos se aplicarán directamente al capital sin penalización");
    }

    private MutualDocumentRequest request(boolean guarantee) {
        return new MutualDocumentRequest(
                new PersonDetails("Jeimmi Ubeth", "Calderón Valle", "La Libertad",
                        "Antiguo Cuscatlán, Municipio de La Libertad Este", "CERO TRES-UNO",
                        "Femenino", "CUARENTA Y CINCO", "Estudiante"),
                new PersonDetails("Luis Arístides", "Díaz Madrid", "La Libertad",
                        "Santa Tecla, Municipio de La Libertad Sur", "CERO CUATRO-DOS",
                        "Masculino", "TREINTA Y SEIS", "Empresario"),
                new MutualTerms("SETECIENTOS CINCUENTA CON VEINTIDÓS CENTAVOS", "SEIS MESES",
                        "NUEVE DE OCTUBRE DE DOS MIL VEINTISÉIS", "SEIS",
                        "CIENTO OCHENTA Y CINCO", "BANCO PROMÉRICA", "DOS CERO CERO",
                        "TRES PUNTO SETENTA Y CINCO", "UNO", "GASTOS PERSONALES",
                        guarantee, guarantee ? "VEINTITRÉS DE ENERO DE DOS MIL VEINTISIETE" : "",
                        "TRES", "Distrito de Santa Tecla", "Distrito de Santa Tecla",
                        "La Libertad", "NUEVE DE ABRIL DE DOS MIL VEINTISÉIS",
                        "NUEVE HORAS CON TREINTA MINUTOS", "No", "No"),
                new LegalAgentDetails("Ana Evelyn", "Valladares Parada", "San Salvador",
                        "Distrito de San Salvador", "Femenino", "Notario"));
    }
}
