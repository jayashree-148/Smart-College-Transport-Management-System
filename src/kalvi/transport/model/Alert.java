package kalvi.transport.model;

import java.time.LocalDate;

/** A dashboard alert. Natural ordering = most urgent first, so a PriorityQueue<Alert> pops the worst alert. */
public class Alert implements Comparable<Alert> {
    public enum Severity { CRITICAL, WARNING, INFO }

    private final Severity severity;
    private final String kind;      // BREAKDOWN, DOCUMENT, SERVICE, FEE, ...
    private final String title;
    private final String detail;
    private final LocalDate date;   // due / event date (may be null)

    public Alert(Severity severity, String kind, String title, String detail, LocalDate date) {
        this.severity = severity; this.kind = kind; this.title = title; this.detail = detail; this.date = date;
    }

    public Severity getSeverity() { return severity; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public LocalDate getDate() { return date; }

    @Override
    public int compareTo(Alert o) {
        int c = severity.compareTo(o.severity);
        if (c != 0) return c;
        if (date != null && o.date != null) { c = date.compareTo(o.date); if (c != 0) return c; }
        return title.compareTo(o.title);
    }
}
