package co.wethinkcode.healthsafe;

/**
 * generatedAt is a String (ISO-8601, e.g. "2026-09-08T10:15:30.123Z"),
 * not a java.time.Instant. Jackson's default configuration can't
 * serialise Instant without an extra module (jackson-datatype-jsr310)
 * explicitly registered - discovered via a real 500 error during
 * testing, not caught beforehand since Java code couldn't be compiled
 * before handoff. A String sidesteps the problem entirely: it's still
 * a valid, standard timestamp format, with zero extra configuration.
 */

public record ScheduleResponse(
        String wardId,
        String department,
        int alertLevel,
        int doctorsOnCall,
        String note,
        String generatedAt
) {}
