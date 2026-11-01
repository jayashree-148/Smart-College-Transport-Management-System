package kalvi.transport.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Serves the dashboard files (HTML/CSS/JS) from the web/ folder. */
public class StaticHandler implements HttpHandler {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml", "png", "image/png", "map", "application/json", "json", "application/json", "ico", "image/x-icon");
    private final Path root;

    public StaticHandler(Path root) { this.root = root.toAbsolutePath().normalize(); }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        if (p.equals("/")) p = "/index.html";
        Path file = root.resolve(p.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {          // blocks ../ path traversal
            byte[] msg = "404 Not found".getBytes();
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        String name = file.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        ex.getResponseHeaders().set("Cache-Control", "no-store, max-age=0");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
