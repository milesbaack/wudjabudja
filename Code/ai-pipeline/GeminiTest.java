import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 1 (INFRA-05) test for the Gemini prompts. No dependencies, Java 11+.
 *
 * Run from this folder:
 *   java GeminiTest.java itinerary
 *   java GeminiTest.java briefing
 *
 * Re-run the checks on a saved response without calling Gemini again:
 *   java GeminiTest.java itinerary --from-file output/itinerary_raw.json
 *
 * Needs the GEMINI_API_KEY environment variable. GEMINI_MODEL is optional
 * (defaults to gemini-3.8-flash).
 *
 * This is a throwaway test harness. The real client goes in the Spring Boot
 * backend in Sprint 5 (AI-02, AI-03) and should use Jackson instead of the
 * tiny JSON helper at the bottom of this file.
 */
public class GeminiTest {

    static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || !(args[0].equals("itinerary") || args[0].equals("briefing"))) {
            System.out.println("Usage: java GeminiTest.java itinerary|briefing [--from-file path]");
            System.exit(2);
        }
        String prompt = args[0];
        String fromFile = (args.length >= 3 && args[1].equals("--from-file")) ? args[2] : null;

        String tripContextText = Files.readString(Path.of("samples/trip_context.json"));
        Map<String, Object> trip = Json.obj(Json.parse(tripContextText));
        Files.createDirectories(Path.of("output"));

        String raw;
        if (fromFile != null) {
            raw = Files.readString(Path.of(fromFile));
            System.out.println("Loaded saved response from " + fromFile);
        } else {
            raw = callGemini(prompt, tripContextText);
            Path rawPath = Path.of("output", prompt + "_raw.json");
            Files.writeString(rawPath, raw);
            System.out.println("Saved full API response to " + rawPath);
        }

        String modelText = extractModelText(raw);
        Object result;
        try {
            result = Json.parse(modelText);
        } catch (RuntimeException e) {
            System.out.println("FAIL: Gemini's answer is not valid JSON (" + e.getMessage() + ")");
            System.out.println(modelText);
            System.exit(1);
            return;
        }
        Path outPath = Path.of("output", prompt + "_output.json");
        Files.writeString(outPath, Json.pretty(result));
        System.out.println("Saved Gemini's JSON answer to " + outPath);

        List<String> problems = prompt.equals("itinerary")
                ? checkItinerary(trip, Json.obj(result))
                : checkBriefing(trip, Json.obj(result));

        System.out.println();
        if (problems.isEmpty()) {
            System.out.println("PASS: every check passed.");
        } else {
            System.out.println("FAIL: " + problems.size() + " problem(s)");
            for (String p : problems) System.out.println("  - " + p);
            System.exit(1);
        }
    }

    // ---------- the API call ----------

    static String callGemini(String prompt, String tripContextText) throws IOException, InterruptedException {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("Set the GEMINI_API_KEY environment variable first (see README.md).");
            System.exit(2);
        }
        String model = System.getenv().getOrDefault("GEMINI_MODEL", DEFAULT_MODEL);
        String system = Files.readString(Path.of("prompts", prompt + "_system.txt"));
        String schema = Files.readString(Path.of("schemas", prompt + "_schema.json"));

        // Request shape from Google's Interactions API quickstart (structured output section).
        String body = "{"
                + "\"model\":" + Json.quote(model) + ","
                + "\"system_instruction\":" + Json.quote(system) + ","
                + "\"input\":" + Json.quote(tripContextText) + ","
                + "\"response_format\":{\"type\":\"text\",\"mime_type\":\"application/json\",\"schema\":" + schema + "}"
                + "}";

        System.out.println("Calling " + model + " with the " + prompt + " prompt...");
        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("x-goog-api-key", key)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            System.out.println("Gemini returned HTTP " + res.statusCode() + ":");
            System.out.println(res.body());
            System.exit(1);
        }
        return res.body();
    }

    /** The answer lives at steps[type=model_output].content[type=text].text */
    static String extractModelText(String raw) {
        Map<String, Object> interaction = Json.obj(Json.parse(raw));
        StringBuilder text = new StringBuilder();
        for (Object s : Json.arr(interaction.get("steps"))) {
            Map<String, Object> step = Json.obj(s);
            if (!"model_output".equals(step.get("type"))) continue;
            for (Object c : Json.arr(step.get("content"))) {
                Map<String, Object> part = Json.obj(c);
                if ("text".equals(part.get("type"))) text.append(part.get("text"));
            }
        }
        if (text.length() == 0) {
            System.out.println("FAIL: no model_output text in the response. Full response is in output/.");
            System.exit(1);
        }
        return text.toString();
    }

    // ---------- checks the schema can't enforce (these become AI-03) ----------

    static List<String> checkItinerary(Map<String, Object> trip, Map<String, Object> out) {
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Object>> people = new LinkedHashMap<>();
        for (Object p : Json.arr(trip.get("participants"))) {
            Map<String, Object> m = Json.obj(p);
            people.put((String) m.get("participant_id"), m);
        }
        LocalDate start = LocalDate.parse((String) trip.get("start_date"));
        int numDays = Json.num(trip.get("num_days")).intValue();

        List<Object> days = Json.arr(out.get("days"));
        if (days.size() != numDays) problems.add("expected " + numDays + " days, got " + days.size());

        for (int d = 0; d < days.size(); d++) {
            Map<String, Object> day = Json.obj(days.get(d));
            String date = (String) day.get("date");
            String expected = start.plusDays(d).toString();
            if (!expected.equals(date)) problems.add("day " + (d + 1) + " is " + date + ", expected " + expected);

            int splitBlocks = 0;
            for (Object b : Json.arr(day.get("blocks"))) {
                Map<String, Object> block = Json.obj(b);
                String where = date + " " + block.get("block");
                List<Object> subgroups = Json.arr(block.get("subgroups"));
                if (subgroups.size() > 1) splitBlocks++;

                Set<String> seen = new HashSet<>();
                for (Object sg : subgroups) {
                    Map<String, Object> sub = Json.obj(sg);
                    List<Object> ids = Json.arr(sub.get("participant_ids"));
                    double cap = Double.MAX_VALUE;
                    int latestWake = 0;
                    for (Object idObj : ids) {
                        String id = (String) idObj;
                        if (!seen.add(id)) problems.add(where + ": " + id + " is in more than one subgroup");
                        Map<String, Object> person = people.get(id);
                        if (person == null) { problems.add(where + ": unknown participant " + id); continue; }
                        cap = Math.min(cap, Json.num(person.get("per_block_cap_usd")).doubleValue());
                        latestWake = Math.max(latestWake, minutes((String) person.get("wake_time")));
                    }
                    for (Object o : Json.arr(sub.get("options"))) {
                        Map<String, Object> opt = Json.obj(o);
                        String name = where + " \"" + opt.get("title") + "\"";
                        double cost = Json.num(opt.get("estimated_cost_usd")).doubleValue();
                        if (cost > cap) problems.add(name + " costs $" + cost + ", cap is $" + cap);
                        int startsAt = minutes((String) opt.get("start_time"));
                        if (startsAt < latestWake + 60) {
                            problems.add(name + " starts " + opt.get("start_time") + ", before latest wake time + 1 hour");
                        }
                    }
                }
                if (!seen.equals(people.keySet())) problems.add(where + ": subgroups cover " + seen + ", expected " + people.keySet());
            }
            if (splitBlocks > 1) problems.add(date + " has " + splitBlocks + " split blocks, max is 1");
        }
        return problems;
    }

    static List<String> checkBriefing(Map<String, Object> trip, Map<String, Object> out) {
        List<String> problems = new ArrayList<>();
        Set<String> expected = new HashSet<>();
        for (Object p : Json.arr(trip.get("participants"))) expected.add((String) Json.obj(p).get("participant_id"));
        Set<String> got = new HashSet<>();
        for (Object s : Json.arr(out.get("souvenirs"))) got.add((String) Json.obj(s).get("participant_id"));
        if (!got.equals(expected)) problems.add("souvenirs cover " + got + ", expected " + expected);
        return problems;
    }

    /** "07:30", "07:30:00" or "07:30:00Z" -> minutes after midnight */
    static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    // ---------- tiny JSON helper (test harness only; the backend uses Jackson) ----------

    static class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) {
            Json p = new Json(text);
            p.ws();
            Object v = p.value();
            p.ws();
            if (p.i != p.s.length()) throw new RuntimeException("extra text at position " + p.i);
            return v;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }

        @SuppressWarnings("unchecked")
        static List<Object> arr(Object o) { return o == null ? new ArrayList<>() : (List<Object>) o; }

        static Number num(Object o) { return (Number) o; }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object value() {
            if (i >= s.length()) throw new RuntimeException("unexpected end");
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws(); expect(':'); ws();
                m.put(k, value());
                ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        private List<Object> array() {
            List<Object> a = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return a; }
            while (true) {
                ws();
                a.add(value());
                ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect(']');
                return a;
            }
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            }
        }

        private Number number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new RuntimeException("unexpected '" + s.charAt(i) + "' at position " + i);
            return Double.parseDouble(s.substring(st, i));
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw new RuntimeException("expected '" + c + "' at position " + i);
            i++;
        }

        static String quote(String v) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : v.toCharArray()) {
                switch (c) {
                    case '"': b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break;
                    case '\t': b.append("\\t"); break;
                    default:
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                }
            }
            return b.append('"').toString();
        }

        static String pretty(Object v) {
            StringBuilder b = new StringBuilder();
            write(b, v, "");
            return b.append('\n').toString();
        }

        private static void write(StringBuilder b, Object v, String indent) {
            String in = indent + "  ";
            if (v instanceof Map) {
                Map<String, Object> m = obj(v);
                if (m.isEmpty()) { b.append("{}"); return; }
                b.append("{\n");
                int n = 0;
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    b.append(in).append(quote(e.getKey())).append(": ");
                    write(b, e.getValue(), in);
                    b.append(++n < m.size() ? ",\n" : "\n");
                }
                b.append(indent).append('}');
            } else if (v instanceof List) {
                List<Object> a = arr(v);
                if (a.isEmpty()) { b.append("[]"); return; }
                b.append("[\n");
                for (int k = 0; k < a.size(); k++) {
                    b.append(in);
                    write(b, a.get(k), in);
                    b.append(k + 1 < a.size() ? ",\n" : "\n");
                }
                b.append(indent).append(']');
            } else if (v instanceof String) {
                b.append(quote((String) v));
            } else if (v instanceof Double) {
                double d = (Double) v;
                b.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d));
            } else {
                b.append(v);
            }
        }
    }
}
