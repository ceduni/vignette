package org.titiplex.service;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.persistence.model.Language;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class LanguageImportService {

    private final LanguageService languageService;

    public LanguageImportService(LanguageService languageService) {
        this.languageService = languageService;
    }

    public List<Language> loadLanguagesFromCsv(Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) throw new IOException("File does not exist: " + csvPath);
        if (!Files.isReadable(csvPath)) throw new IOException("File is not readable: " + csvPath);

        File file = csvPath.toFile();
        if (file.length() == 0) throw new IOException("File is empty: " + csvPath);

        CsvMapper csvMapper = new CsvMapper();
        csvMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        CsvSchema schema = CsvSchema.emptySchema().withHeader();

        MappingIterator<Language> iterator = csvMapper
                .readerFor(Language.class)
                .with(schema)
                .readValues(file);

        List<Language> result = iterator.readAll();
        for (Language language : result) normalizeEmptyStrings(language);
        return result;
    }

    private static void normalizeEmptyStrings(Language language) {
        if (isBlank(language.getIso639P3code())) language.setIso639P3code(null);
        if (isBlank(language.getFamilyId())) language.setFamilyId(null);
        if (isBlank(language.getParentId())) language.setParentId(null);
        if (isBlank(language.getDescription())) language.setDescription(null);
        if (isBlank(language.getMarkupDescription())) language.setMarkupDescription(null);
        if (isBlank(language.getCountryIds())) language.setCountryIds(null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Transactional
    public int importIfEmpty(Path csvPath) throws IOException {
        if (languageService.count() > 0) return 0;

        List<Language> all = loadLanguagesFromCsv(csvPath);

        // chunk pour éviter de tout pousser d’un coup
        int chunkSize = 2000;
        int inserted = 0;

        for (int i = 0; i < all.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, all.size());
            List<Language> chunk = new ArrayList<>(all.subList(i, end));
            languageService.saveAll(chunk);
            inserted += chunk.size();
        }
        return inserted;
    }
}
