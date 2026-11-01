package kalvi.transport.web;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kalvi.transport.dao.*;
import kalvi.transport.model.UserRecord;
import kalvi.transport.service.TransportService;
import static kalvi.transport.web.Json.obj;

/** Lets an admin connect the running server to MySQL from the web page (no restart, no file editing). */
public class DbAdmin {
    private final AtomicReference<TransportDao> ref;
    private final String propsFile;
    private final TransportService service;
    private volatile DbConfig cfg;
    private volatile String note;

    public DbAdmin(AtomicReference<TransportDao> ref, String propsFile, DbConfig cfg, String note, TransportService service) {
        this.ref = ref; this.propsFile = propsFile; this.cfg = cfg; this.note = note == null ? "" : note; this.service = service;
    }

    public String note() { return note; }

    public Map<String, Object> status() {
        return obj("mysql", ref.get().isMySql(), "note", note, "host", cfg.host(), "port", cfg.port(), "user", cfg.user);
    }

    public synchronized Map<String, Object> connect(String host, String port, String user, String password) {
        host = host == null || host.isBlank() ? "localhost" : host.trim();
        port = port == null || port.isBlank() ? "3306" : port.trim();
        user = user == null || user.isBlank() ? "root" : user.trim();
        if (!host.matches("[A-Za-z0-9._-]{1,100}")) throw new IllegalArgumentException("Host looks wrong - use localhost or an IP / server name");
        if (!port.matches("\\d{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) throw new IllegalArgumentException("Port must be a number such as 3306");
        DbConfig next = cfg.withHostPort(host, port).withUser(user).withPassword(password == null ? "" : password);

        DriverLoader.ensure();                                    // also retries the driver download if it failed earlier
        TransportDao fresh;
        try { fresh = WebApp.connectOrSetup(next); }
        catch (Exception e) { note = WebApp.explain(e, next); throw new IllegalArgumentException(note); }

        TransportDao old = ref.get();                             // keep accounts created while in demo mode
        if (old != fresh) for (UserRecord u : old.users()) if (fresh.findUser(u.userId()) == null) fresh.saveUser(u);
        ref.set(fresh);
        service.reset();
        cfg = next; note = "";
        String saved;
        try { next.save(propsFile); saved = " Settings saved in db.properties."; }
        catch (java.io.IOException e) { saved = " (could not write db.properties: " + e.getMessage() + ")"; }
        fresh.log("SYSTEM", "Connected to MySQL at " + host + ":" + port + " as " + user);
        return obj("ok", true, "message", "Connected to MySQL." + saved);
    }
}
