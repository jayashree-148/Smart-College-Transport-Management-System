package kalvi.transport.model;

/** A pickup stop (or the campus depot) with its GPS position. Immutable value object. */
public record Stop(String id, String name, double lat, double lng) {}
