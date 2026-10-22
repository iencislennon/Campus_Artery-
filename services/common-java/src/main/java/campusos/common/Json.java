package campusos.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON codec (no external library): objects -> Map, arrays -> List, numbers -> Long/Double. */
public final class Json {
    private final String text;
    private int pos;

    private Json(String text) { this.text = text; }

    public static Object parse(String text) {
        if (text == null || text.isBlank()) return null;
        Json parser = new Json(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) throw parser.fail("Unexpected trailing characters");
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private Object readValue() {
        skipWhitespace();
        if (pos >= text.length()) throw fail("Unexpected end of input");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f', 'n' -> readLiteral();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            map.put(key, readValue());
            skipWhitespace();
            if (peek() == ',') { pos++; continue; }
            expect('}');
            return map;
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(readValue());
            skipWhitespace();
            if (peek() == ',') { pos++; continue; }
            expect(']');
            return list;
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            char esc = text.charAt(pos++);
            switch (esc) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> { out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4; }
                default -> out.append(esc);
            }
        }
        throw fail("Unterminated string");
    }

    private Object readLiteral() {
        if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        if (text.startsWith("null", pos)) { pos += 4; return null; }
        throw fail("Invalid literal");
    }

    private Object readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        String raw = text.substring(start, pos);
        if (raw.isEmpty()) throw fail("Unexpected character");
        try {
            return raw.contains(".") || raw.contains("e") || raw.contains("E") ? (Object) Double.parseDouble(raw) : (Object) Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw fail("Invalid number");
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private char peek() { return pos < text.length() ? text.charAt(pos) : '\0'; }

    private void expect(char c) {
        if (peek() != c) throw fail("Expected '" + c + "'");
        pos++;
    }

    private ApiException fail(String message) {
        return ApiException.badRequest("Invalid JSON: " + message + " at position " + pos);
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) out.append("null");
        else if (value instanceof String s) writeString(out, s);
        else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map<?, ?> map) writeMap(out, map);
        else if (value instanceof Iterable<?> items) writeIterable(out, items);
        else writeString(out, value.toString());
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        String separator = "";
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.append(separator);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            write(out, entry.getValue());
            separator = ",";
        }
        out.append('}');
    }

    private static void writeIterable(StringBuilder out, Iterable<?> items) {
        out.append('[');
        String separator = "";
        for (Object item : items) {
            out.append(separator);
            write(out, item);
            separator = ",";
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        out.append('"');
    }
}
