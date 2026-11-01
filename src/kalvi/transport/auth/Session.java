package kalvi.transport.auth;

/** A signed-in browser. The token lives in an HttpOnly cookie; the session itself only lives in server memory. */
public record Session(String token, String userId, String fullName, Role role, long expiresAt) { }