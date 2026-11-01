package kalvi.transport.web;

import java.util.*;

/** Minimal JSON writer (no libraries): Map -> object, Iterable/array -> array, everything else by type. */
public final class Json {
    private Json() { }

    /** obj("a", 1, "b", "x") -> insertion-ordered map. */
    public static Map<String, Object> obj(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("key/value pairs expected");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    public static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof Boolean || (o instanceof Number n && !isBad(n))) { sb.append(o); return; }
        if (o instanceof Number) { sb.append("null"); return; }
        if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) { if (!first) sb.append(','); first = false; write(sb, x); }
            sb.append(']');
            return;
        }
        if (o.getClass().isArray() && o instanceof Object[] arr) { write(sb, Arrays.asList(arr)); return; }
        quote(sb, o.toString());      // String, Enum, LocalDate, ...
    }

    private static boolean isBad(Number n) {
        return (n instanceof Double d && (d.isNaN() || d.isInfinite())) || (n instanceof Float f && (f.isNaN() || f.isInfinite()));
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }
}
