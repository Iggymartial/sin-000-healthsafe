package co.wethinkcode.healthsafe;

import java.util.List;

public record WardRecord(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        List<String> notes
) {}
