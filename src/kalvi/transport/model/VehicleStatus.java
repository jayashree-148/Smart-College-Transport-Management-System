package kalvi.transport.model;

/** Fleet status. The dashboard shows ACTIVE as "In Transit", SPARE as "Idle", MAINTENANCE as "Broken Down". */
public enum VehicleStatus {
    ACTIVE("In Transit"), SPARE("Idle"), MAINTENANCE("Broken Down");

    private final String label;
    VehicleStatus(String label) { this.label = label; }
    public String label() { return label; }
}
