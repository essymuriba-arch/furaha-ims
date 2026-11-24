package ke.furaha.ims;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Lightweight, zero-dependency JSON serializer and parser for Furaha REST API.
 */
public final class JsonUtil {

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return escape((String) obj);
        if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(escape(String.valueOf(entry.getKey()))).append(':');
                sb.append(toJson(entry.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (obj instanceof Collection) {
            Collection<?> col = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return escape(String.valueOf(obj));
    }

    public static String resultSetToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder sb = new StringBuilder("[");
        boolean firstRow = true;
        while (rs.next()) {
            if (!firstRow) sb.append(',');
            firstRow = false;
            sb.append('{');
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(',');
                String label = md.getColumnLabel(i);
                Object val = rs.getObject(i);
                sb.append(escape(label)).append(':');
                if (val == null) {
                    sb.append("null");
                } else if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val);
                } else {
                    sb.append(escape(String.valueOf(val)));
                }
            }
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    public static String getField(String json, String fieldName) {
        if (json == null) return null;
        String pattern = "\"" + fieldName + "\"\\s*:\\s*";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;
        int start = m.end();
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '"') {
            StringBuilder val = new StringBuilder();
            boolean esc = false;
            for (int i = start + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (esc) {
                    if (c == 'n') val.append('\n');
                    else if (c == 'r') val.append('\r');
                    else if (c == 't') val.append('\t');
                    else val.append(c);
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    return val.toString();
                } else {
                    val.append(c);
                }
            }
            return val.toString();
        } else if (first == 'n' && json.startsWith("null", start)) {
            return null;
        } else {
            // number or boolean
            int end = start;
            while (end < json.length() && ",}] \r\n\t".indexOf(json.charAt(end)) == -1) {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    /**
     * Extracts array elements when an array of objects is passed, e.g. "items": [ {...}, {...} ]
     */
    public static List<String> extractArrayItems(String json, String arrayField) {
        List<String> list = new ArrayList<>();
        if (json == null) return list;
        int arrIdx = json.indexOf("\"" + arrayField + "\"");
        if (arrIdx == -1) {
            // If the whole JSON is already an array
            arrIdx = json.indexOf('[');
            if (arrIdx == -1) return list;
        } else {
            arrIdx = json.indexOf('[', arrIdx);
            if (arrIdx == -1) return list;
        }

        int depth = 0;
        int itemStart = -1;
        boolean inStr = false;
        boolean esc = false;

        for (int i = arrIdx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;

            if (c == '{') {
                if (depth == 0) itemStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && itemStart != -1) {
                    list.add(json.substring(itemStart, i + 1));
                    itemStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return list;
    }
}
