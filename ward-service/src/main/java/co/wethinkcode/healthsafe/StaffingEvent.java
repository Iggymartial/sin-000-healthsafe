package co.wethinkcode.healthsafe;

/**
 * Matches staffing-service's ScheduleResponse field-for-field - this is
 * what actually arrives on staffing-events-topic. A duplicated copy,
 * not a shared import, same convention as MqConfig and WardRecord
 * across this project's independent modules.
 */
public record StaffingEvent(
        String wardId,
        String department,
        int alertLevel,
        int doctorsOnCall,
        String note,
        String generatedAt
) {}
