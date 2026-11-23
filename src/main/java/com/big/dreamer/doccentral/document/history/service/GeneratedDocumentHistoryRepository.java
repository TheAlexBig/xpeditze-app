package com.big.dreamer.doccentral.document.history.service;

import com.big.dreamer.doccentral.document.carsale.model.CarSaleDocumentRequest;
import com.big.dreamer.doccentral.document.carsale.model.PersonDetails;
import com.big.dreamer.doccentral.document.history.model.GeneratedDocumentMetadata;
import com.big.dreamer.doccentral.document.mutual.model.MutualDocumentRequest;
import com.big.dreamer.doccentral.document.marriage.model.MarriageDocumentRequest;
import com.big.dreamer.doccentral.storage.ApplicationDirectories;
import com.big.dreamer.doccentral.storage.LocalJsonFileWriter;
import com.big.dreamer.doccentral.storage.RecoverableJsonFileReader;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GeneratedDocumentHistoryRepository {

    private static final String CAR_SALE_TYPE = "car-sale";
    private static final String MUTUAL_TYPE = "mutual";
    private static final String MARRIAGE_TYPE = "marriage";
    private final Path historyFile;
    private final ObjectMapper objectMapper;

    public GeneratedDocumentHistoryRepository(ApplicationDirectories directories, ObjectMapper objectMapper) {
        this.historyFile = directories.generatedDocumentsHistoryFile();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void initialize() {
        if (Files.notExists(historyFile)) {
            write(List.of());
        }
    }

    public synchronized List<GeneratedDocumentMetadata> findAll() {
        try {
            GeneratedDocumentMetadata[] documents = RecoverableJsonFileReader.read(
                    historyFile, objectMapper, GeneratedDocumentMetadata[].class);
            return sortByMostRecent(List.of(documents));
        } catch (IOException exception) {
            throw new DocumentHistoryStorageException("Unable to read generated document history.", exception);
        }
    }

    public synchronized Optional<GeneratedDocumentMetadata> findById(String id) {
        return findAll().stream()
                .filter(document -> document.id().equals(id))
                .findFirst();
    }

    public synchronized GeneratedDocumentMetadata saveCarSale(
            String fileName,
            String createdAt,
            CarSaleDocumentRequest document,
            Map<String, Object> draft) {
        GeneratedDocumentMetadata metadata = new GeneratedDocumentMetadata(
                UUID.randomUUID().toString(),
                3,
                CAR_SALE_TYPE,
                fileName,
                createdAt,
                title(document, draft),
                partyName(draft, "personStates", document.buyer()),
                partyName(draft, "vendorStates", document.seller()),
                vehicleName(document, draft),
                document,
                draft == null ? Map.of() : draft,
                null,
                null);

        List<GeneratedDocumentMetadata> documents = new ArrayList<>(findAll());
        documents.add(0, metadata);
        write(sortByMostRecent(documents));
        return metadata;
    }

    public synchronized GeneratedDocumentMetadata saveMutual(
            String fileName,
            String createdAt,
            MutualDocumentRequest document,
            Map<String, Object> draft) {
        String debtor = mutualPartyName(document.debtor());
        String creditor = mutualPartyName(document.creditor());
        GeneratedDocumentMetadata metadata = new GeneratedDocumentMetadata(
                UUID.randomUUID().toString(),
                2,
                MUTUAL_TYPE,
                fileName,
                createdAt,
                "Mutuo - " + creditor + " / " + debtor,
                debtor,
                creditor,
                "",
                null,
                draft == null ? Map.of() : draft,
                document,
                null);
        List<GeneratedDocumentMetadata> documents = new ArrayList<>(findAll());
        documents.add(0, metadata);
        write(sortByMostRecent(documents));
        return metadata;
    }

    public synchronized GeneratedDocumentMetadata saveMarriage(
            String fileName,
            String createdAt,
            MarriageDocumentRequest document,
            Map<String, Object> draft) {
        String partyOne = fullName(document.partyOne().person().givenName(), document.partyOne().person().lastName());
        String partyTwo = fullName(document.partyTwo().person().givenName(), document.partyTwo().person().lastName());
        GeneratedDocumentMetadata metadata = new GeneratedDocumentMetadata(
                UUID.randomUUID().toString(), 3, MARRIAGE_TYPE, fileName, createdAt,
                "Matrimonio - " + partyOne + " / " + partyTwo,
                partyOne, partyTwo, "", null,
                draft == null ? Map.of() : draft, null, document);
        List<GeneratedDocumentMetadata> documents = new ArrayList<>(findAll());
        documents.add(0, metadata);
        write(sortByMostRecent(documents));
        return metadata;
    }

    private List<GeneratedDocumentMetadata> sortByMostRecent(List<GeneratedDocumentMetadata> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(
                        GeneratedDocumentMetadata::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String title(CarSaleDocumentRequest document, Map<String, Object> draft) {
        return "Compra venta - " + vehicleName(document, draft);
    }

    private String vehicleName(CarSaleDocumentRequest document, Map<String, Object> draft) {
        return String.join(" ",
                draftValue(draft, "carStates", "marca", document.vehicle().brand()),
                draftValue(draft, "carStates", "modelo", document.vehicle().model()),
                draftValue(draft, "carStates", "placa", document.vehicle().licensePlate())).trim();
    }

    private String personName(Map<String, Object> draft, String section, String fallback) {
        String givenName = draftValue(draft, section, "nombre", "");
        String lastName = draftValue(draft, section, "apellido", "");
        String name = fullName(givenName, lastName);
        return name.isBlank() ? fallback : name;
    }

    private String partyName(
            Map<String, Object> draft,
            String section,
            PersonDetails person) {
        String companyName = draftValue(draft, section, "sociedad_nombre", "");
        if (!companyName.isBlank()) return companyName;
        if (person.company() != null && !person.company().legalName().isBlank()) {
            return person.company().legalName();
        }
        return personName(draft, section, fullName(person.givenName(), person.lastName()));
    }

    private String draftValue(
            Map<String, Object> draft,
            String section,
            String field,
            String fallback) {
        if (draft != null && draft.get(section) instanceof Map<?, ?> values) {
            Object value = values.get(field);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return fallback;
    }

    private String fullName(String givenName, String lastName) {
        return String.join(" ", givenName, lastName).trim();
    }

    private String mutualPartyName(PersonDetails person) {
        return person.company() == null
                ? fullName(person.givenName(), person.lastName())
                : person.company().legalName();
    }

    private void write(List<GeneratedDocumentMetadata> documents) {
        try {
            LocalJsonFileWriter.write(historyFile, objectMapper.writeValueAsString(documents));
        } catch (IOException exception) {
            throw new DocumentHistoryStorageException("Unable to save generated document history.", exception);
        }
    }
}
