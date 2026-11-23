package com.tycooncraft.tyc2.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java, zero-dependency, RFC-8259-compliant JSON parser and serializer for
 * datapacks, external configuration files, and SavedData serialization.
 */
public final class JsonLite {

    private static final int MAX_DEPTH = 128;

    private JsonLite() {
    }

    /**
     * Exception thrown when JSON input violates RFC-8259 syntax.
     */
    public static final class JsonParseException extends RuntimeException {
        private final int line;
        private final int column;

        /**
         * Creates a parse exception with line and column metadata.
         *
         * @param message description of the syntax error
         * @param line 1-based line number
         * @param column 1-based column number
         */
        public JsonParseException(String message, int line, int column) {
            super(message + " at line " + line + ", column " + column);
            this.line = line;
            this.column = column;
        }

        /**
         * Returns the 1-based line number where parsing failed.
         *
         * @return line number
         */
        public int line() {
            return line;
        }

        /**
         * Returns the 1-based column number where parsing failed.
         *
         * @return column number
         */
        public int column() {
            return column;
        }
    }

    /**
     * Parses any valid RFC-8259 JSON value into standard Java objects:
     * {@link LinkedHashMap}, {@link ArrayList}, {@link String}, {@link Long},
     * {@link Double}, {@link Boolean}, or {@code null}.
     *
     * @param json JSON text
     * @return parsed Java object
     * @throws JsonParseException if the input is invalid JSON
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonParseException("JSON input must not be null", 1, 1);
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected trailing character '" + parser.peek() + "'");
        }
        return value;
    }

    /**
     * Parses a JSON object into a {@link Map}.
     *
     * @param json JSON object text
     * @return parsed map
     * @throws JsonParseException if the input is invalid or not a JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object parsed = parse(json);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new JsonParseException("Expected JSON object", 1, 1);
        }
        return (Map<String, Object>) parsed;
    }

    /**
     * Parses a JSON array into a {@link List}.
     *
     * @param json JSON array text
     * @return parsed list
     * @throws JsonParseException if the input is invalid or not a JSON array
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object parsed = parse(json);
        if (!(parsed instanceof List<?>)) {
            throw new JsonParseException("Expected JSON array", 1, 1);
        }
        return (List<Object>) parsed;
    }

    /**
     * Serializes a Java value into compact RFC-8259 JSON.
     *
     * @param value object to serialize
     * @return compact JSON string
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, false, 0);
        return sb.toString();
    }

    /**
     * Serializes a Java value into human-readable pretty-printed JSON (2-space indent).
     *
     * @param value object to serialize
     * @return formatted JSON string
     */
    public static String toPrettyJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, true, 0);
        return sb.toString();
    }

    /**
     * Safely casts a value to a string-keyed map.
     *
     * @param value candidate value
     * @return map, or {@code null} if not a map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * Safely casts a value to a list.
     *
     * @param value candidate value
     * @return list, or {@code null} if not a list
     */
    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List<?>) {
            return (List<Object>) value;
        }
        return null;
    }

    /**
     * Reads a string field from a JSON map with fallback default.
     *
     * @param map source map
     * @param key field key
     * @param defaultValue fallback value
     * @return string value or fallback
     */
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        return val instanceof String ? (String) val : defaultValue;
    }

    /**
     * Reads a required non-blank string field from a JSON map.
     *
     * @param map source map
     * @param key field key
     * @return string value
     * @throws IllegalArgumentException if missing or blank
     */
    public static String requireString(Map<String, Object> map, String key) {
        String val = getString(map, key, null);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Missing required string field: " + key);
        }
        return val;
    }

    /**
     * Reads a boolean field from a JSON map with fallback default.
     *
     * @param map source map
     * @param key field key
     * @param defaultValue fallback value
     * @return boolean value or fallback
     */
    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : defaultValue;
    }

    /**
     * Reads an integer field from a JSON map with fallback default.
     *
     * @param map source map
     * @param key field key
     * @param defaultValue fallback value
     * @return int value or fallback
     */
    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : defaultValue;
    }

    /**
     * Reads a long field from a JSON map with fallback default.
     *
     * @param map source map
     * @param key field key
     * @param defaultValue fallback value
     * @return long value or fallback
     */
    public static long getLong(Map<String, Object> map, String key, long defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).longValue() : defaultValue;
    }

    /**
     * Reads a double field from a JSON map with fallback default.
     *
     * @param map source map
     * @param key field key
     * @param defaultValue fallback value
     * @return double value or fallback
     */
    public static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).doubleValue() : defaultValue;
    }

    /**
     * Reads a list of strings from a JSON map.
     *
     * @param map source map
     * @param key field key
     * @return list of strings (never {@code null})
     */
    public static List<String> getStringList(Map<String, Object> map, String key) {
        if (map == null) {
            return Collections.emptyList();
        }
        List<Object> raw = asArray(map.get(key));
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof String) {
                out.add((String) item);
            }
        }
        return out;
    }

    private static void writeValue(StringBuilder sb, Object value, boolean pretty, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON nesting exceeds max depth " + MAX_DEPTH);
        }
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Number) {
            writeNumber(sb, (Number) value);
        } else if (value instanceof Enum<?>) {
            writeString(sb, ((Enum<?>) value).name());
        } else if (value instanceof Map<?, ?>) {
            writeMap(sb, (Map<?, ?>) value, pretty, depth);
        } else if (value instanceof Collection<?>) {
            writeCollection(sb, (Collection<?>) value, pretty, depth);
        } else if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            List<Object> list = new ArrayList<>(arr.length);
            Collections.addAll(list, arr);
            writeCollection(sb, list, pretty, depth);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeNumber(StringBuilder sb, Number number) {
        if (number instanceof Double) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("JSON does not support non-finite number: " + d);
            }
            sb.append(d);
        } else if (number instanceof Float) {
            float f = number.floatValue();
            if (!Float.isFinite(f)) {
                throw new IllegalArgumentException("JSON does not support non-finite number: " + f);
            }
            sb.append(f);
        } else {
            sb.append(number);
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, boolean pretty, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (pretty) {
                sb.append('\n');
                indent(sb, depth + 1);
            }
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(pretty ? ": " : ":");
            writeValue(sb, entry.getValue(), pretty, depth + 1);
        }
        if (pretty) {
            sb.append('\n');
            indent(sb, depth);
        }
        sb.append('}');
    }

    private static void writeCollection(StringBuilder sb, Collection<?> col, boolean pretty, int depth) {
        if (col.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean first = true;
        for (Object item : col) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (pretty) {
                sb.append('\n');
                indent(sb, depth + 1);
            }
            writeValue(sb, item, pretty, depth + 1);
        }
        if (pretty) {
            sb.append('\n');
            indent(sb, depth);
        }
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String src;
        private final int len;
        private int pos;
        private int line = 1;
        private int col = 1;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
        }

        boolean isAtEnd() {
            return pos >= len;
        }

        char peek() {
            return src.charAt(pos);
        }

        char advance() {
            char c = src.charAt(pos++);
            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            return c;
        }

        void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    advance();
                } else {
                    break;
                }
            }
        }

        JsonParseException error(String msg) {
            return new JsonParseException(msg, line, col);
        }

        Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("Maximum JSON nesting depth exceeded");
            }
            skipWhitespace();
            if (isAtEnd()) {
                throw error("Unexpected end of JSON input");
            }
            char c = peek();
            switch (c) {
                case '{':
                    return parseObject(depth + 1);
                case '[':
                    return parseArray(depth + 1);
                case '"':
                    return parseString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private Map<String, Object> parseObject(int depth) {
            advance(); // '{'
            skipWhitespace();
            Map<String, Object> map = new LinkedHashMap<>();
            if (!isAtEnd() && peek() == '}') {
                advance();
                return map;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || peek() != '"') {
                    throw error("Expected string key in JSON object");
                }
                String key = parseString();
                skipWhitespace();
                if (isAtEnd() || peek() != ':') {
                    throw error("Expected ':' after object key '" + key + "'");
                }
                advance();
                Object value = parseValue(depth);
                map.put(key, value);
                skipWhitespace();
                if (isAtEnd()) {
                    throw error("Unterminated JSON object");
                }
                char next = peek();
                if (next == '}') {
                    advance();
                    return map;
                }
                if (next != ',') {
                    throw error("Expected ',' or '}' in JSON object");
                }
                advance();
            }
        }

        private List<Object> parseArray(int depth) {
            advance(); // '['
            skipWhitespace();
            List<Object> list = new ArrayList<>();
            if (!isAtEnd() && peek() == ']') {
                advance();
                return list;
            }
            while (true) {
                list.add(parseValue(depth));
                skipWhitespace();
                if (isAtEnd()) {
                    throw error("Unterminated JSON array");
                }
                char next = peek();
                if (next == ']') {
                    advance();
                    return list;
                }
                if (next != ',') {
                    throw error("Expected ',' or ']' in JSON array");
                }
                advance();
            }
        }

        private String parseString() {
            advance(); // opening '"'
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = advance();
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw error("Unescaped control character in string");
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw error("Unterminated escape sequence in string");
                    }
                    char esc = advance();
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            sb.append(parseHex4());
                            break;
                        default:
                            throw error("Invalid escape sequence '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseHex4() {
            if (pos + 4 > len) {
                throw error("Incomplete unicode escape sequence");
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                char h = advance();
                int digit = Character.digit(h, 16);
                if (digit < 0) {
                    throw error("Invalid hex digit '" + h + "' in unicode escape");
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private Number parseNumber() {
            int start = pos;
            if (peek() == '-') {
                advance();
                if (isAtEnd()) {
                    throw error("Invalid number '-'");
                }
            }
            char first = peek();
            if (first == '0') {
                advance();
                if (!isAtEnd() && isDigit(peek())) {
                    throw error("Leading zeros are not allowed in JSON numbers");
                }
            } else if (first >= '1' && first <= '9') {
                while (!isAtEnd() && isDigit(peek())) {
                    advance();
                }
            } else {
                throw error("Invalid number start '" + first + "'");
            }

            boolean isFloat = false;
            if (!isAtEnd() && peek() == '.') {
                isFloat = true;
                advance();
                if (isAtEnd() || !isDigit(peek())) {
                    throw error("Expected digit after decimal point");
                }
                while (!isAtEnd() && isDigit(peek())) {
                    advance();
                }
            }
            if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
                isFloat = true;
                advance();
                if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                    advance();
                }
                if (isAtEnd() || !isDigit(peek())) {
                    throw error("Expected digit in exponent");
                }
                while (!isAtEnd() && isDigit(peek())) {
                    advance();
                }
            }

            String token = src.substring(start, pos);
            if (!isFloat) {
                try {
                    return Long.parseLong(token);
                } catch (NumberFormatException ignored) {
                    // Fall through to double for huge integers
                }
            }
            try {
                double d = Double.parseDouble(token);
                if (!Double.isFinite(d)) {
                    throw error("Numeric literal out of range: " + token);
                }
                return d;
            } catch (NumberFormatException ex) {
                throw error("Invalid number: " + token);
            }
        }

        private void expectLiteral(String literal) {
            for (int i = 0; i < literal.length(); i++) {
                if (isAtEnd() || advance() != literal.charAt(i)) {
                    throw error("Expected literal '" + literal + "'");
                }
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
