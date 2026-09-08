package co.wethinkcode.healthsafe;

import java.util.List;

/**
 * Matches the shape returned by ingestion-service's GET /wards. This is
 * a duplicated copy, not a shared import - each service here is an
 * independent Maven module with no parent pom, the same convention
 * already established by MqConfig in this project.
 */
public record WardRecord(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        List<String> notes
) {}
