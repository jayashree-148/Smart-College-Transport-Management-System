package kalvi.transport.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import kalvi.transport.auth.AuthService;
import kalvi.transport.auth.Role;
import kalvi.transport.auth.Session;
import kalvi.transport.model.UserRecord;
import kalvi.transport.service.TransportService;
import static kalvi.transport.web.Json.obj;

/** Maps /api/... URLs to TransportService calls and writes JSON. No business logic and no SQL here. */
public class ApiHandler implements HttpHandler {
    public static final String COOKIE = "ct_session";
    private final TransportService service;
    private final AuthService auth;
    private final DbAdmin db;

    public ApiHandler(TransportService service, AuthService auth, DbAdmin db) { this.service = service; this.auth = auth; this.db = db; }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object body;
        try {
            body = route(ex);
        } catch (HttpError e) {
            status = e.status; body = obj("error", e.getMessage());
        } catch (NoSuchElementException e) {
            status = 404; body = obj("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            status = 400; body = obj("error", e.getMessage());
        } catch (IOException | RuntimeException e) {
            System.err.println("[api] Request failed: " + e);
            status = 500; body = obj("error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
        byte[] out = Json.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(out); }
    }

    private Object route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (method.equals("OPTIONS")) return obj("ok", true);
        String path = ex.getRequestURI().getPath();
        Map<String, String> q = params(ex);
        String[] seg = path.substring("/api/".length()).split("/");
        boolean post = method.equals("POST");

        // ---- public: sign in / register / who am I
        if (seg[0].equals("auth")) return authRoute(ex, seg.length > 1 ? seg[1] : "", q, post);

        // ---- everything else needs a signed-in user
        Session me = auth.session(cookie(ex, COOKIE));
        if (me == null) throw new HttpError(401, "Please sign in");
        if (post && !me.role().canWrite())
            throw new HttpError(403, "Your account is read-only (VIEWER). Ask an admin for STAFF access.");
        if (seg[0].equals("db")) {
            if (me.role() != Role.ADMIN) throw new HttpError(403, "Only an ADMIN can change the database connection");
            if (post && seg.length > 1 && seg[1].equals("connect")) return db.connect(q.get("host"), q.get("port"), q.get("user"), q.get("password"));
            return db.status();
        }
        if (seg[0].equals("users")) return usersRoute(me, seg.length > 1 ? seg[1] : "", q, post);

        switch (seg[0]) {
            case "system" -> { Map<String, Object> sys = new LinkedHashMap<>(service.system()); sys.put("dbNote", db.note()); return sys; }
            case "dashboard" -> { return service.dashboard(); }
            case "map" -> { return service.map(); }
            case "routes" -> { return service.routes(); }
            case "crew" -> { return service.crew(q.get("role")); }
            case "students" -> { return service.students(); }
            case "tracking" -> { return service.tracking(); }
            case "reports" -> { return service.reports(); }
            case "events" -> { return service.events(); }
            case "alerts" -> { return service.alertsPage(q.get("severity")); }
            case "vehicles" -> {
                if (seg.length == 1) return service.vehicles(q.get("q"), q.get("status"), q.get("route"),
                        q.get("sort"), q.get("dir"), num(q.get("page"), 0), clamp(num(q.get("size"), 15), 1, 200));
                if (seg.length == 2) return service.vehicle(decode(seg[1]));
                if (seg.length == 3 && seg[2].equals("status") && post) {
                    return service.setVehicleStatus(decode(seg[1]), need(q, "value"));
                }
            }
            case "fees" -> {
                if (seg.length == 1) return service.fees();
                if (seg.length == 2 && seg[1].equals("pay") && post)
                    return service.payFee(need(q, "studentId"), num(need(q, "amount"), 0));
            }
            case "attendance" -> {
                if (post) return service.markAttendance(need(q, "studentId"), Boolean.parseBoolean(need(q, "present")));
            }
            case "plan" -> {
                return service.plan(q.get("breakdown"), num(q.get("after"), 1), post);
            }
            default -> { }
        }
        throw new NoSuchElementException("Unknown endpoint: " + method + " " + path);
    }

    // ---------- auth endpoints ----------
    private Object authRoute(HttpExchange ex, String action, Map<String, String> q, boolean post) {
        String token = cookie(ex, COOKIE);
        switch (action) {
            case "me" -> {
                Session s = auth.session(token);
                return obj("user", s == null ? null : userJson(s), "mysql", service.isMySql(), "dbNote", db.note(), "version", WebApp.VERSION);
            }
            case "login" -> {
                if (post) {
                    Session s = auth.login(q.get("userId"), q.get("password"));
                    setCookie(ex, s.token(), (int) (AuthService.SESSION_MS / 1000));
                    return obj("user", userJson(s));
                }
            }
            case "register" -> {
                if (post) {
                    Session s = auth.register(q.get("userId"), q.get("fullName"), q.get("password"));
                    setCookie(ex, s.token(), (int) (AuthService.SESSION_MS / 1000));
                    return obj("user", userJson(s));
                }
            }
            case "logout" -> {
                if (post) {
                    auth.logout(token);
                    setCookie(ex, "", 0);
                    return obj("ok", true);
                }
            }
            case "password" -> {
                if (post) {
                    Session s = auth.session(token);
                    if (s == null) throw new HttpError(401, "Please sign in");
                    auth.changePassword(s, q.get("oldPassword"), need(q, "newPassword"));
                    return obj("ok", true);
                }
            }
            default -> { }
        }
        throw new NoSuchElementException("Unknown endpoint: /api/auth/" + action);
    }

    private Object usersRoute(Session me, String action, Map<String, String> q, boolean post) {
        if (me.role() != Role.ADMIN) throw new HttpError(403, "Only an ADMIN can manage users");
        if (action.isEmpty() && !post) {
            List<Object> rows = new ArrayList<>();
            for (UserRecord u : auth.users())
                rows.add(obj("userId", u.userId(), "fullName", u.fullName(), "role", u.role(), "createdAt", u.createdAt(),
                        "locked", u.lockedUntil() > System.currentTimeMillis(), "you", u.userId().equals(me.userId())));
            return obj("users", rows);
        }
        if (post) switch (action) {
            case "role" -> { auth.setRole(me, need(q, "userId"), need(q, "role")); return obj("ok", true); }
            case "reset" -> { auth.resetPassword(me, need(q, "userId"), need(q, "newPassword")); return obj("ok", true); }
            case "delete" -> { auth.deleteUser(me, need(q, "userId")); return obj("ok", true); }
            default -> { }
        }
        throw new NoSuchElementException("Unknown endpoint: /api/users/" + action);
    }

    private static Map<String, Object> userJson(Session s) {
        return obj("userId", s.userId(), "fullName", s.fullName(), "role", s.role().name(), "canWrite", s.role().canWrite());
    }

    private static String cookie(HttpExchange ex, String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String h : headers)
            for (String part : h.split(";")) {
                String p = part.trim();
                if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
            }
        return null;
    }
    private static void setCookie(HttpExchange ex, String value, int maxAgeSeconds) {
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAgeSeconds);
    }

    // ---------- helpers ----------
    private static String need(Map<String, String> q, String k) {
        String v = q.get(k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing parameter: " + k);
        return v;
    }
    private static int num(String s, int dflt) {
        if (s == null || s.isBlank()) return dflt;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Not a number: " + s); }
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static String decode(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    /** Query string + (for POST) application/x-www-form-urlencoded body. */
    private static Map<String, String> params(HttpExchange ex) throws IOException {
        Map<String, String> m = new HashMap<>();
        parse(ex.getRequestURI().getRawQuery(), m);
        if (ex.getRequestMethod().equals("POST")) parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), m);
        return m;
    }
    private static void parse(String s, Map<String, String> m) {
        if (s == null || s.isBlank()) return;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i), v = i < 0 ? "" : pair.substring(i + 1);
            m.put(decode(k), decode(v));
        }
    }
}
