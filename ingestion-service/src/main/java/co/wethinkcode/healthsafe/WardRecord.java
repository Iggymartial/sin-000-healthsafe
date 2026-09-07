package co.wethinkcode.healthsafe;

import java.util.List;

/**
 * The cleaned, normalised shape of one ward record, ready to be served
 * over REST and consumed by ward-service.
 *
 * bedsAvailable is nullable: a null here means "we couldn't determine a
 * trustworthy number" (missing, non-numeric, negative, or unrealistic),
 * not "zero beds" - those are different facts and must not be conflated.
 *
 * notes is never null (may be empty) - it's the audit trail of anything
 * this record's cleaning process had to decide about, so a reviewer can
 * see exactly what happened to a row, not just the final result.
 */
public record WardRecord(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        List<String> notes
) {}
