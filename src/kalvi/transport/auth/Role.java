package kalvi.transport.auth;

/** ADMIN = everything + manage users; STAFF = can change data; VIEWER = read-only. */
public enum Role {
    ADMIN, STAFF, VIEWER;

    public boolean canWrite() { return this != VIEWER; }

    public static Role parse(String s) {
        try { return Role.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("Role must be ADMIN, STAFF or VIEWER"); }
    }
}
