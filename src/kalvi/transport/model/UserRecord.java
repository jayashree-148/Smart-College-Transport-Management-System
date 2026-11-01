package kalvi.transport.model;

/** One login account (table app_users). The password is never stored, only a salted PBKDF2 hash. */
public record UserRecord(String userId, String fullName, String role, String passwordHash,
                         int failedAttempts, long lockedUntil, String createdAt) {

    public UserRecord withFailed(int failed, long lockedUntilMs) {
        return new UserRecord(userId, fullName, role, passwordHash, failed, lockedUntilMs, createdAt);
    }
    public UserRecord withRole(String newRole) {
        return new UserRecord(userId, fullName, newRole, passwordHash, failedAttempts, lockedUntil, createdAt);
    }
    public UserRecord withPassword(String newHash) {
        return new UserRecord(userId, fullName, role, newHash, 0, 0, createdAt);
    }
}
