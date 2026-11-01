package kalvi.transport.auth;

import kalvi.transport.dao.TransportDao;
import kalvi.transport.model.UserRecord;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Login logic: user ID + password, roles, sessions, lockout.
 * Only talks to TransportDao (MySQL table app_users, or data/demo/users.tsv in demo mode).
 */
public class AuthService {
    public static final int MAX_FAILED = 5;
    public static final long LOCK_MS = 5 * 60_000L, SESSION_MS = 8 * 3_600_000L;
    private static final Pattern USER_ID = Pattern.compile("[a-z0-9._-]{3,20}");
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Used to burn the same time for unknown users, so response time does not reveal which IDs exist. */
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password-1");

    private final TransportDao dao;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();   // token -> Session

    public AuthService(TransportDao dao) { this.dao = dao; }

    /** First start: create admin / Admin@123 so you can sign in (change it from the user menu). */
    public void seedDefaultAdmin() {
        if (dao.users().isEmpty()) {
            dao.saveUser(new UserRecord("admin", "Administrator", "ADMIN", PasswordHasher.hash("Admin@123"), 0, 0, now()));
            dao.log("AUTH", "Default account 'admin' created");
            System.out.println("[auth] First run: created user 'admin' with password 'Admin@123' - change it after signing in.");
        }
    }

    // ---------------------------------------------------------------- register / login / logout
    public Session register(String userId, String fullName, String password) {
        String id = normalize(userId);
        checkPassword(password);
        String name = fullName == null ? "" : fullName.trim();
        if (name.length() < 2 || name.length() > 60) throw new IllegalArgumentException("Enter your full name (2-60 characters)");
        if (dao.findUser(id) != null) throw new IllegalArgumentException("That user ID is already taken");
        dao.saveUser(new UserRecord(id, name, "VIEWER", PasswordHasher.hash(password), 0, 0, now()));
        dao.log("AUTH", "New account '" + id + "' registered (VIEWER)");
        return login(id, password);
    }

    public Session login(String userId, String password) {
        String id = userId == null ? "" : userId.trim().toLowerCase();
        UserRecord u = id.isEmpty() ? null : dao.findUser(id);
        if (u == null) { PasswordHasher.verify(String.valueOf(password), DUMMY_HASH); throw bad(); }
        long now = System.currentTimeMillis();
        if (u.lockedUntil() > now)
            throw new IllegalArgumentException("Account locked after too many wrong passwords. Try again in "
                    + (((u.lockedUntil() - now) / 60_000) + 1) + " minute(s).");
        if (password == null || !PasswordHasher.verify(password, u.passwordHash())) {
            int failed = u.failedAttempts() + 1;
            boolean lock = failed >= MAX_FAILED;
            dao.saveUser(u.withFailed(lock ? 0 : failed, lock ? now + LOCK_MS : 0));
            dao.log("AUTH", "Failed login for '" + id + "'" + (lock ? " - account locked 5 min" : ""));
            throw bad();
        }
        if (u.failedAttempts() != 0 || u.lockedUntil() != 0) dao.saveUser(u.withFailed(0, 0));
        sessions.values().removeIf(s -> s.expiresAt() < now);
        Session s = new Session(newToken(), u.userId(), u.fullName(), Role.parse(u.role()), now + SESSION_MS);
        sessions.put(s.token(), s);
        dao.log("AUTH", "'" + id + "' signed in (" + u.role() + ")");
        return s;
    }

    public Session session(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (s.expiresAt() < System.currentTimeMillis()) { sessions.remove(token); return null; }
        return s;
    }

    public void logout(String token) {
        Session s = token == null ? null : sessions.remove(token);
        if (s != null) dao.log("AUTH", "'" + s.userId() + "' signed out");
    }

    // ---------------------------------------------------------------- own account
    public void changePassword(Session me, String oldPw, String newPw) {
        UserRecord u = dao.findUser(me.userId());
        if (u == null || !PasswordHasher.verify(String.valueOf(oldPw), u.passwordHash()))
            throw new IllegalArgumentException("Current password is wrong");
        checkPassword(newPw);
        dao.saveUser(u.withPassword(PasswordHasher.hash(newPw)));
        sessions.values().removeIf(s -> s.userId().equals(me.userId()) && !s.token().equals(me.token()));   // sign out other devices
        dao.log("AUTH", "'" + me.userId() + "' changed their password");
    }

    // ---------------------------------------------------------------- admin: user management
    public List<UserRecord> users() {
        List<UserRecord> l = new ArrayList<>(dao.users());
        l.sort(Comparator.comparing(UserRecord::userId));
        return l;
    }

    public void setRole(Session admin, String userId, String role) {
        UserRecord u = need(userId);
        Role r = Role.parse(role);
        if (u.role().equals("ADMIN") && r != Role.ADMIN && adminCount() <= 1)
            throw new IllegalArgumentException("There must be at least one ADMIN");
        dao.saveUser(u.withRole(r.name()));
        sessions.replaceAll((t, s) -> s.userId().equals(u.userId()) ? new Session(t, s.userId(), s.fullName(), r, s.expiresAt()) : s);
        dao.log("AUTH", "'" + admin.userId() + "' set role of '" + u.userId() + "' to " + r);
    }

    public void resetPassword(Session admin, String userId, String newPw) {
        UserRecord u = need(userId);
        checkPassword(newPw);
        dao.saveUser(u.withPassword(PasswordHasher.hash(newPw)));
        sessions.values().removeIf(s -> s.userId().equals(u.userId()));
        dao.log("AUTH", "'" + admin.userId() + "' reset the password of '" + u.userId() + "'");
    }

    public void deleteUser(Session admin, String userId) {
        UserRecord u = need(userId);
        if (u.userId().equals(admin.userId())) throw new IllegalArgumentException("You cannot delete your own account");
        if (u.role().equals("ADMIN") && adminCount() <= 1) throw new IllegalArgumentException("There must be at least one ADMIN");
        dao.deleteUser(u.userId());
        sessions.values().removeIf(s -> s.userId().equals(u.userId()));
        dao.log("AUTH", "'" + admin.userId() + "' deleted user '" + u.userId() + "'");
    }

    // ---------------------------------------------------------------- helpers
    private UserRecord need(String userId) {
        UserRecord u = userId == null ? null : dao.findUser(userId.trim().toLowerCase());
        if (u == null) throw new NoSuchElementException("No such user: " + userId);
        return u;
    }
    private long adminCount() { return dao.users().stream().filter(x -> x.role().equals("ADMIN")).count(); }
    private static IllegalArgumentException bad() { return new IllegalArgumentException("Invalid user ID or password"); }
    private static String now() { return LocalDateTime.now().withNano(0).toString().replace('T', ' '); }
    private static String newToken() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return HexFormat.of().formatHex(b); }

    private static String normalize(String userId) {
        String id = userId == null ? "" : userId.trim().toLowerCase();
        if (!USER_ID.matcher(id).matches())
            throw new IllegalArgumentException("User ID must be 3-20 characters: letters, digits, dot, dash or underscore");
        return id;
    }
    private static void checkPassword(String p) {
        if (p == null || p.length() < 8 || p.length() > 64 || p.chars().noneMatch(Character::isLetter) || p.chars().noneMatch(Character::isDigit))
            throw new IllegalArgumentException("Password must be 8-64 characters with at least one letter and one digit");
    }
}
