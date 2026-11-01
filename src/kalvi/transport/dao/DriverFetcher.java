package kalvi.transport.dao;

import kalvi.transport.AppDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Makes sure the MySQL JDBC driver is in lib/. If it is missing it is downloaded once from Maven Central
 * (or a mirror) and checked against the published SHA-1, so nobody has to hunt for the jar by hand.
 * Never fails the start: without internet the app falls back to demo mode and says why.
 */
public final class DriverFetcher {
    static final String VERSION = "8.4.0";
    static final String FILE = "mysql-connector-j-" + VERSION + ".jar";
    static final List<String> MIRRORS = List.of(
            "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/" + VERSION + "/" + FILE,
            "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/" + VERSION + "/" + FILE,
            "https://maven-central.storage-download.googleapis.com/maven2/com/mysql/mysql-connector-j/" + VERSION + "/" + FILE,
            "https://maven.aliyun.com/repository/public/com/mysql/mysql-connector-j/" + VERSION + "/" + FILE);

    public static Path find(Path lib) {
        if (!Files.isDirectory(lib)) return null;
        try (Stream<Path> s = Files.list(lib)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().matches("mysql-connector.*\\.jar")).findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }

    public static void main(String[] args) { ensure(AppDir.of("lib")); }

    /** Downloads the driver into lib if it is not there yet. */
    public static void ensure(Path lib) {
        if (find(lib) != null) return;
        System.out.println("[driver] MySQL driver not found in lib/ - downloading mysql-connector-j " + VERSION + " (about 2.5 MB, one time only) ...");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();
        String lastError = "no internet";
        for (String url : MIRRORS) {
            try {
                byte[] jar = get(http, url);
                String expected = new String(get(http, url + ".sha1")).trim().split("\\s+")[0].toLowerCase();
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(jar));
                if (!actual.equals(expected)) throw new IOException("checksum mismatch - file discarded");
                Files.createDirectories(lib);
                Path tmp = lib.resolve(FILE + ".part");
                Files.write(tmp, jar);
                Files.move(tmp, lib.resolve(FILE), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[driver] Installed lib/" + FILE + " (checksum OK).");
                return;
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        System.err.println("[driver] Could not download the MySQL driver (" + lastError + ").");
        System.err.println("[driver] Connect to the internet and run again, or copy mysql-connector-j-8.x.x.jar into lib/ yourself.");
    }

    private static byte[] get(HttpClient http, String url) throws Exception {
        HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    }
}
