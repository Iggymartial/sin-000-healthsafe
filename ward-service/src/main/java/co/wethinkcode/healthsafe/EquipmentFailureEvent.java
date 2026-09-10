package co.wethinkcode.healthsafe;

/** What gets reported and published when a ward detects equipment failure. */
public record EquipmentFailureEvent(
        String wardId,
        String equipment,
        String issue,
        String reportedAt
) {}
