import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpUtils {

    // Lê o corpo da requisição HTTP e converte para String
    public static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Envia resposta JSON
    public static void sendJson(HttpExchange ex, int code, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            try { ex.sendResponseHeaders(500, -1); } catch (Exception ignore) {}
        }
    }

    // Extrai uma string de um JSON simples (sem libs)
    public static String extractString(String json, String field) {
        String token = "\"" + field + "\"";
        int i = json.indexOf(token);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + token.length());
        if (colon < 0) return null;
        int first = json.indexOf('"', colon + 1);
        int second = json.indexOf('"', first + 1);
        if (first < 0 || second < 0) return null;
        return json.substring(first + 1, second);
    }

    // Extrai um número inteiro de um JSON simples (sem libs)
    public static Integer extractInt(String json, String field) {
        String token = "\"" + field + "\"";
        int i = json.indexOf(token);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + token.length());
        if (colon < 0) return null;
        int end = json.indexOf(',', colon + 1);
        if (end < 0) end = json.indexOf('}', colon + 1);
        String num = json.substring(colon + 1, end).replace("\"", "").trim();
        try { return Integer.parseInt(num); } catch (Exception e) { return null; }
    }
}
