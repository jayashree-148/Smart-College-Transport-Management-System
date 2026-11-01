package kalvi.transport.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Reads db.properties (url, user, password). Environment variables DB_URL / DB_USER / DB_PASSWORD override it. */
public final class DbConfig {
    public final String url, user, password;

    private DbConfig(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
    }

    public static DbConfig load(String file) throws IOException {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) { p.load(in); }
        return new DbConfig(
                env("DB_URL", p.getProperty("url")),
                env("DB_USER", p.getProperty("user", "root")),
                env("DB_PASSWORD", p.getProperty("password", "")));
    }

    public static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/college_transport_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";

    /** db.properties if it can be read, otherwise localhost defaults (so the page can still offer the connect form). */
    public static DbConfig loadOrDefault(String file) {
        try { return load(file); } catch (java.io.IOException e) { return new DbConfig(DEFAULT_URL, "root", ""); }
    }

    public DbConfig withPassword(String pw) { return new DbConfig(url, user, pw); }
    public DbConfig withUser(String u) { return new DbConfig(url, u, password); }
    public DbConfig withHostPort(String host, String port) {
        return new DbConfig(url.replaceFirst("//[^/?]+", java.util.regex.Matcher.quoteReplacement("//" + host + ":" + port)), user, password);
    }
    public String host() { var m = java.util.regex.Pattern.compile("//([^/:?]+)(?::(\\d+))?").matcher(url); return m.find() ? m.group(1) : "localhost"; }
    public String port() { var m = java.util.regex.Pattern.compile("//([^/:?]+)(?::(\\d+))?").matcher(url); return m.find() && m.group(2) != null ? m.group(2) : "3306"; }

    /** Saves url, user and password into db.properties (other lines are kept). */
    public void save(String file) throws IOException {
        java.nio.file.Path f = java.nio.file.Paths.get(file);
        java.util.List<String> lines = java.nio.file.Files.exists(f)
                ? new java.util.ArrayList<>(java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8)) : new java.util.ArrayList<>();
        String[][] kv = {{"url", url}, {"user", user}, {"password", password}};
        for (String[] e : kv) {
            String line = e[0] + "=" + e[1].replace("\\", "\\\\");
            boolean done = false;
            for (int i = 0; i < lines.size(); i++)
                if (lines.get(i).trim().startsWith(e[0] + "=") || lines.get(i).trim().startsWith(e[0] + " =")) { lines.set(i, line); done = true; }
            if (!done) lines.add(line);
        }
        java.nio.file.Files.write(f, lines, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Writes password=... into db.properties (replaces the existing line, keeps everything else). */
    public static void savePassword(String file, String pw) throws IOException {
        java.nio.file.Path f = java.nio.file.Paths.get(file);
        java.util.List<String> lines = new java.util.ArrayList<>(java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8));
        boolean done = false;
        for (int i = 0; i < lines.size(); i++)
            if (lines.get(i).trim().startsWith("password")) { lines.set(i, "password=" + pw.replace("\\", "\\\\")); done = true; }
        if (!done) lines.add("password=" + pw.replace("\\", "\\\\"));
        java.nio.file.Files.write(f, lines, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Same server, but without the database name (used by DbSetup to CREATE DATABASE). */
    public String serverUrl() {
        int slash = url.indexOf('/', "jdbc:mysql://".length());
        if (slash < 0) return url;
        int q = url.indexOf('?', slash);
        return url.substring(0, slash + 1) + (q >= 0 ? url.substring(q) : "");
    }
}
