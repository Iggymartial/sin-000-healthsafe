package co.wethinkcode.healthsafe;

public record EquipmentFailureEvent(
        String wardId,
        String equipment,
        String issue,
        String reportedAt
) {}
