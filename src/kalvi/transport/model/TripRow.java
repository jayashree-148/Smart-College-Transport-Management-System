package kalvi.transport.model;

/** A saved trip row (daily_trips table). */
public record TripRow(int tripId, String date, String busId, String route, int minutes, String status) {}
