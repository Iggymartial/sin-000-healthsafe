package co.wethinkcode.healthsafe;

/** A consistent JSON error shape across every endpoint in this service. */
public record ErrorResponse(String error) {}
