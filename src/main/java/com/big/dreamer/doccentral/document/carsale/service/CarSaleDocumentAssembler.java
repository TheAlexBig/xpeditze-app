package com.big.dreamer.doccentral.document.carsale.service;

import com.big.dreamer.doccentral.document.carsale.model.CarDetails;
import com.big.dreamer.doccentral.document.carsale.model.CarSaleDocumentRequest;
import com.big.dreamer.doccentral.document.carsale.model.CompanyDetails;
import com.big.dreamer.doccentral.document.carsale.model.DocumentDetails;
import com.big.dreamer.doccentral.document.carsale.model.LegalAgentDetails;
import com.big.dreamer.doccentral.document.carsale.model.PersonDetails;
import com.big.dreamer.doccentral.document.carsale.template.CarSaleTemplateRepository;
import com.big.dreamer.doccentral.document.render.LegalDocument;
import com.big.dreamer.doccentral.document.render.LegalDocument.Section;
import com.big.dreamer.doccentral.document.render.LegalDocument.Signature;
import com.big.dreamer.doccentral.document.template.EditableTemplateRepository;
import com.big.dreamer.doccentral.document.text.LegalDocumentText;
import com.big.dreamer.doccentral.document.text.NotarialIdentificationText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CarSaleDocumentAssembler {
    private final CarSaleTemplateRepository templates;

    CarSaleDocumentAssembler(CarSaleTemplateRepository templates) {
        this.templates = templates;
    }

    LegalDocument assemble(CarSaleDocumentRequest request) {
        Map<String, String> blocks = templates.findAll();
        Map<String, String> values = values(request);
        String declaration = String.join(" ",
                render(blocks, "parties-document.txt", values),
                render(blocks, "car-document.txt", values),
                render(blocks, "terms-document.txt", values),
                render(blocks, "first-section-end.txt", values));
        String authentic = String.join(" ",
                render(blocks, "legal-authentic.txt", values),
                render(blocks, "parties-authentic.txt", values),
                render(blocks, "car-authentic.txt", values),
                render(blocks, "terms-authentic.txt", values),
                render(blocks, "second-section-end.txt", values));
        List<Signature> signatures = List.of(
                new Signature(fullName(request.buyer()), buyerTitle(request.buyer())),
                new Signature(fullName(request.seller()), sellerTitle(request.seller())));
        return new LegalDocument(List.of(
                new Section(declaration, signatures),
                new Section(authentic, signatures)));
    }

    private Map<String, String> values(CarSaleDocumentRequest request) {
        PersonDetails seller = request.seller();
        PersonDetails buyer = request.buyer();
        CarDetails car = request.vehicle();
        DocumentDetails details = request.document();
        LegalAgentDetails agent = request.legalAgent();
        boolean bothPartiesFeminine = feminineParty(seller) && feminineParty(buyer);
        boolean bothAppearingWomen = female(seller) && female(buyer);
        String both = bothPartiesFeminine ? "ambas contratantes" : "ambos contratantes";
        Map<String, String> values = new LinkedHashMap<>();
        addPerson(values, "seller", seller);
        addPerson(values, "buyer", buyer);
        values.put("sellerDocumentAppearance", documentAppearance(seller, sellerTitle(seller)));
        values.put("buyerDocumentAppearance", documentAppearance(buyer, buyerTitle(buyer)));
        values.put("sellerAuthenticAppearance", authenticAppearance(
                seller, sellerTitle(seller), details.identifiesSeller()));
        values.put("buyerAuthenticAppearance", authenticAppearance(
                buyer, buyerTitle(buyer), details.identifiesBuyer()));
        values.put("sellerTitle", sellerTitle(seller));
        values.put("buyerTitle", buyerTitle(buyer));
        values.put("sellerKnowledge", NotarialIdentificationText.knowledge(details.identifiesSeller()));
        values.put("buyerKnowledge", NotarialIdentificationText.knowledge(details.identifiesBuyer()));
        values.put("sellerRole", seller.company() != null ? "la sociedad vendedora" : gendered(seller, "el vendedor", "la vendedora"));
        values.put("sellerOwner", seller.company() != null ? "dueña" : gendered(seller, "dueño", "dueña"));
        values.put("sellerHolder", seller.company() != null ? "poseedora" : gendered(seller, "poseedor", "poseedora"));
        values.put("buyerRole", buyer.company() != null ? "la sociedad compradora" : gendered(buyer, "el comprador", "la compradora"));
        values.put("buyerSaleTarget", buyer.company() != null ? "a la sociedad compradora" : gendered(buyer, "al comprador", "a la compradora"));
        values.put("buyerRoleCapitalized", buyer.company() != null ? "La sociedad compradora" : gendered(buyer, "El comprador", "La compradora"));
        values.put("buyerReceived", buyer.company() != null ? "recibida" : gendered(buyer, "recibido", "recibida"));
        values.put("bothContracting", both);
        values.put("bothContractingCapitalized", capitalize(both));
        values.put("appearingParties", bothAppearingWomen ? "las comparecientes" : "los comparecientes");
        values.put("licensePlate", value(car.licensePlate()));
        values.put("brand", value(car.brand()));
        values.put("model", value(car.model()));
        values.put("color", value(car.color()));
        values.put("factoryYear", value(car.factoryYear()));
        values.put("capacityDetails", heavy(car) ? "" : "CAPACIDAD: " + value(car.capacity()) + ";");
        values.put("domain", value(car.domain()));
        values.put("vehicleClass", value(car.vehicleClass()));
        values.put("vehicleType", value(car.vehicleType()));
        values.put("heavyTruckDetails", heavyDetails(car));
        values.put("tractionDetails", blank(car.traction()) ? "" : "TRACCIÓN: " + car.traction() + ";");
        values.put("engineNumber", value(car.engineNumber()));
        values.put("chassisNumber", value(car.chassisNumber()));
        values.put("vinNumber", value(car.vinNumber()));
        values.put("garment", value(details.garment()));
        values.put("institutionPhrase", blank(details.institution()) ? "" : " con " + details.institution());
        values.put("price", LegalDocumentText.currencyFromWords(details.price()));
        values.put("signingSettlement", value(details.settlement()));
        values.put("signingState", value(details.state()));
        values.put("signDate", value(details.signDate()));
        values.put("signHour", value(details.signHour()));
        values.put("notaryName", fullName(agent.givenName(), agent.lastName()));
        values.put("notaryTitle", legalAgentTitle(agent));
        values.put("notarySettlement", value(agent.settlement()));
        values.put("notaryState", value(agent.state()));
        return values;
    }

    private void addPerson(Map<String, String> values, String prefix, PersonDetails person) {
        values.put(prefix + "Name", fullName(person));
        values.put(prefix + "Age", value(person.age()));
        values.put(prefix + "Job", value(person.job()));
        values.put(prefix + "Settlement", value(person.settlement()));
        values.put(prefix + "State", value(person.state()));
        values.put(prefix + "Document", value(person.document()));
    }

    private String heavyDetails(CarDetails car) {
        if (!heavy(car)) return "";
        return "EJES: " + value(car.axles()) + "; TARA: " + value(car.tare())
                + "; TIPO DE CAPACIDAD: " + value(car.capacityType())
                + "; CAPACIDAD DE CARGA: " + value(car.loadCapacity())
                + "; CAPACIDAD MÁXIMA: " + value(car.maximumCapacity()) + "; ";
    }

    private String render(Map<String, String> blocks, String name, Map<String, String> values) {
        return EditableTemplateRepository.render(blocks.get(name), values);
    }

    private boolean heavy(CarDetails car) {
        return "camión pesado".equalsIgnoreCase(car.vehicleClass());
    }

    private boolean female(PersonDetails person) {
        return "femenino".equalsIgnoreCase(person.gender());
    }

    private boolean feminineParty(PersonDetails person) {
        return person.company() != null || female(person);
    }

    private String gendered(PersonDetails person, String masculine, String feminine) {
        return female(person) ? feminine : masculine;
    }

    private String sellerTitle(PersonDetails person) {
        return person.company() != null ? "LA SOCIEDAD VENDEDORA"
                : gendered(person, "EL VENDEDOR", "LA VENDEDORA");
    }

    private String buyerTitle(PersonDetails person) {
        return person.company() != null ? "LA SOCIEDAD COMPRADORA"
                : gendered(person, "EL COMPRADOR", "LA COMPRADORA");
    }

    private String documentAppearance(PersonDetails person, String title) {
        String base = personIdentity(person, "con Documento Único de Identidad número " + value(person.document()));
        if (person.company() == null) {
            return base + ", y en adelante me denominaré “" + title + "”";
        }
        return base + ", " + companyRepresentation(person.company(), "mi")
                + ", en tal carácter en adelante me denominaré “" + title + "”";
    }

    private String authenticAppearance(PersonDetails person, String title, String identified) {
        String identification = NotarialIdentificationText.knowledge(identified)
                + " e identifico con Documento Único de Identidad homologado número " + value(person.document());
        String base = personIdentity(person, identification);
        if (person.company() == null) {
            return base + ", quien actúa en su carácter personal y a quien en adelante denominaré “" + title + "”";
        }
        return base + ", " + companyRepresentation(person.company(), "su")
                + ", personería que doy fe de ser legítima y suficiente por haber tenido a la vista: "
                + value(person.company().authorityEvidence())
                + "; en tal carácter en adelante denominaré “" + title + "”";
    }

    private String personIdentity(PersonDetails person, String identification) {
        return fullName(person) + ", de " + value(person.age()) + " años de edad, "
                + value(person.job()) + ", del domicilio de " + value(person.settlement())
                + ", Departamento de " + value(person.state()) + ", " + identification;
    }

    private String companyRepresentation(CompanyDetails company, String possessive) {
        String abbreviation = blank(company.abbreviatedName())
                ? ""
                : ", que se puede abreviar " + company.abbreviatedName();
        return "actuando en " + possessive + " calidad de " + value(company.representativeCapacity())
                + " de la sociedad " + value(company.legalName()) + abbreviation
                + ", persona jurídica, de nacionalidad " + value(company.nationality())
                + ", del domicilio de " + value(company.settlement())
                + ", Departamento de " + value(company.state())
                + ", con Número de Identificación Tributaria " + value(company.taxId());
    }

    private String legalAgentTitle(LegalAgentDetails agent) {
        boolean woman = "femenino".equalsIgnoreCase(agent.gender());
        if ("abogado".equalsIgnoreCase(agent.role())) return woman ? "ABOGADA" : "ABOGADO";
        return woman ? "NOTARIA" : "NOTARIO";
    }

    private String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String fullName(PersonDetails person) {
        return fullName(person.givenName(), person.lastName());
    }

    private String fullName(String first, String last) {
        return (value(first) + " " + value(last)).trim().replaceAll("\\s+", " ")
                .toUpperCase(Locale.forLanguageTag("es-SV"));
    }

    private boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
