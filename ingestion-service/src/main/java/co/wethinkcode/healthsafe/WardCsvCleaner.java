package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cleans the deliberately messy wards-outdated.csv into a normalised,
 * de-duplicated list of WardRecord. Kept separate from IngestionServiceApp
 * so this logic - the actual point of this service - can be unit tested
 * without needing a running Javalin server.
 *
 * Only handles the issue types genuinely present in this CSV (casing,
 * padding, duplicates, missing/placeholder values, invalid numeric
 * values). This file has no date or boolean columns, so no logic exists
 * for those - handling issues that don't exist in the actual data would
 * be untested, unnecessary code.
 *
 * DUPLICATE-MERGE STRATEGY (a deliberate judgement call, not the only
 * valid one): when two rows describe the same ward (same ward_id once
 * casing/padding is normalised), the row with a VALID, USABLE
 * bedsAvailable wins - keeping the more actionable data point rather
 * than an unparseable one. If both or neither are usable, the first
 * occurrence in the file wins. Either way, the surviving record's notes
 * field always states that a duplicate was merged and exactly what was
 * discarded - nothing disappears without a trace. A stricter pipeline
 * might instead reject duplicates outright for manual review; this one
 * prioritises keeping the pipeline running over halting on conflict.
 */
public class WardCsvCleaner {

    // Known spelling/regional variants that refer to the same real
    // department, covering only the variant actually present in this
    // CSV ("Pediatrics" vs "Paediatrics") - not a generic dictionary.
    private static final Map<String, String> DEPARTMENT_SYNONYMS = Map.of(
            "pediatrics", "Paediatrics"
    );

    // Acronym departments that must NOT be title-cased word-by-word -
    // "icu" -> "Icu" would be wrong. Found by actually verifying this
    // cleaner's output against the real CSV before writing any Java,
    // not assumed correct on the first pass.
    private static final List<String> ACRONYMS = List.of("icu");

    // Case-insensitive placeholder values that all mean "no value given".
    private static final List<String> MISSING_PLACEHOLDERS = List.of(
            "", "n/a", "na", "tbd", "unknown", "-", "nan"
    );

    // A hospital ward realistically has at most a few dozen beds. A
    // value like 2023 (almost certainly a stray year) is an unrealistic
    // count, not a real number of beds - flagged rather than trusted.
    private static final int MAX_PLAUSIBLE_BEDS = 200;

    public List<WardRecord> clean(InputStream csvInput) throws IOException, CsvValidationException {
        Map<String, WardRecord> byWardId = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(csvInput, StandardCharsets.UTF_8))) {
            reader.readNext(); // discard header row

            String[] row;
            while ((row = reader.readNext()) != null) {
                WardRecord candidate = cleanRow(row);
                if (candidate == null) {
                    continue; // unusable row (no id, too few columns) - skipped, not crashed on
                }

                WardRecord existing = byWardId.get(candidate.wardId());
                byWardId.put(
                        candidate.wardId(),
                        existing == null ? candidate : mergeDuplicate(existing, candidate)
                );
            }
        }

        return new ArrayList<>(byWardId.values());
    }

    private WardRecord cleanRow(String[] row) {
        if (row.length < 4) {
            return null;
        }

        List<String> notes = new ArrayList<>();

        String wardId = normaliseWardId(row[0]);
        String wing = normaliseWing(row[1], notes);
        String department = normaliseDepartment(row[2], notes);
        Integer bedsAvailable = normaliseBeds(row[3], notes);

        if (wardId == null || wardId.isBlank()) {
            return null; // no usable identifier - nothing to file this record under
        }

        return new WardRecord(wardId, wing, department, bedsAvailable, notes);
    }

    private String normaliseWardId(String raw) {
        return collapseSpaces(raw).toUpperCase();
    }

    private String normaliseWing(String raw, List<String> notes) {
        String trimmed = collapseSpaces(raw);
        if (isPlaceholder(trimmed)) {
            notes.add("wing was missing/placeholder ('" + raw.trim() + "')");
            return null;
        }
        return titleCase(trimmed);
    }

    private String normaliseDepartment(String raw, List<String> notes) {
        String trimmed = collapseSpaces(raw);
        if (isPlaceholder(trimmed)) {
            notes.add("department was missing/placeholder ('" + raw.trim() + "')");
            return null;
        }

        String synonym = DEPARTMENT_SYNONYMS.get(trimmed.toLowerCase());
        if (synonym != null) {
            notes.add("department normalised from spelling variant '" + trimmed + "' to '" + synonym + "'");
            return synonym;
        }

        return titleCase(trimmed);
    }

    private Integer normaliseBeds(String raw, List<String> notes) {
        String trimmed = collapseSpaces(raw);

        if (isPlaceholder(trimmed)) {
            notes.add("bedsAvailable was missing/placeholder ('" + raw.trim() + "')");
            return null;
        }

        int value;
        try {
            value = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            notes.add("bedsAvailable was non-numeric ('" + raw.trim() + "') - flagged for follow-up");
            return null;
        }

        if (value < 0) {
            notes.add("bedsAvailable was negative (" + value + ") - flagged as invalid");
            return null;
        }

        if (value > MAX_PLAUSIBLE_BEDS) {
            notes.add("bedsAvailable was unrealistically large (" + value + ") - flagged as invalid");
            return null;
        }

        return value;
    }

    private WardRecord mergeDuplicate(WardRecord first, WardRecord second) {
        WardRecord winner = (first.bedsAvailable() != null || second.bedsAvailable() == null) ? first : second;
        WardRecord loser = (winner == first) ? second : first;

        List<String> notes = new ArrayList<>(winner.notes());
        notes.add("merged duplicate record for this ward - discarded a row with wing='"
                + loser.wing() + "', department='" + loser.department()
                + "', bedsAvailable=" + loser.bedsAvailable());

        return new WardRecord(winner.wardId(), winner.wing(), winner.department(), winner.bedsAvailable(), notes);
    }

    private boolean isPlaceholder(String value) {
        return MISSING_PLACEHOLDERS.contains(value.toLowerCase());
    }

    private String collapseSpaces(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    private String titleCase(String value) {
        String[] words = value.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            if (ACRONYMS.contains(word)) {
                sb.append(word.toUpperCase());
            } else {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
