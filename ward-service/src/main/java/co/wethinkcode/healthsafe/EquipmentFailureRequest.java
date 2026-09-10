package co.wethinkcode.healthsafe;

/** POST body for reporting an equipment failure: {"equipment": "...", "issue": "..."} */
public record EquipmentFailureRequest(String equipment, String issue) {}
