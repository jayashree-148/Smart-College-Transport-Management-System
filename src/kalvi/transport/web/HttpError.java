package kalvi.transport.web;

/** An error with a specific HTTP status (401 not signed in, 403 not allowed). */
public class HttpError extends RuntimeException {
    public final int status;
    public HttpError(int status, String message) { super(message); this.status = status; }
}
