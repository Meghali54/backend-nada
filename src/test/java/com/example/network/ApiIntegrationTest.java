package com.example.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.network.controller.ApiServer;
import com.example.network.repository.InMemoryGraphRepository;
import com.example.network.service.SocialGraphService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests against a real server bound to a random port. */
class ApiIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    private Javalin app;
    private String base;

    @BeforeEach
    void start() {
        app = ApiServer.create(new SocialGraphService(new InMemoryGraphRepository()));
        app.start(0);
        base = "http://localhost:" + app.port();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    // ---- POST /people -------------------------------------------------------------

    @Test
    void createPerson_returns201_withSequentialIds() throws Exception {
        Resp first = post("/people", "{\"name\": \"Asha\"}");
        assertEquals(201, first.status);
        assertEquals(1, first.json().get("id").asLong());
        assertEquals("Asha", first.json().get("name").asText());

        Resp second = post("/people", "{\"name\": \"Ravi\"}");
        assertEquals(2, second.json().get("id").asLong());
    }

    @Test
    void createPerson_supportsUnicodeNames() throws Exception {
        Resp resp = post("/people", "{\"name\": \"आशा शर्मा\"}");
        assertEquals(201, resp.status);
        assertEquals("आशा शर्मा", resp.json().get("name").asText());

        long other = person("Ravi");
        knows(1, other);
        Resp contacts = get("/people/" + other + "/contacts");
        assertEquals("आशा शर्मा", contacts.json().get(0).get("name").asText());
    }

    @Test
    void createPerson_rejectsBadInput() throws Exception {
        assertEquals(400, post("/people", "{\"name\": \"\"}").status);
        assertEquals(400, post("/people", "{\"name\": \"   \"}").status);
        assertEquals(400, post("/people", "{}").status);
        assertEquals(400, post("/people", "{\"name\": 42}").status);
        assertEquals(400, post("/people", "{\"name\": null}").status);
        assertEquals(400, post("/people", "{not json").status);
        assertEquals(400, post("/people", "").status);
        assertEquals(400, post("/people", "[1,2]").status);
    }

    @Test
    void failedCreate_doesNotConsumeAnId() throws Exception {
        post("/people", "{\"name\": \"\"}");
        assertEquals(1, post("/people", "{\"name\": \"Asha\"}").json().get("id").asLong());
    }

    // ---- POST /knows --------------------------------------------------------------

    @Test
    void knows_returns204_andIsIdempotentAndMutual() throws Exception {
        long a = person("A");
        long b = person("B");

        assertEquals(204, post("/knows", "{\"a\":" + a + ",\"b\":" + b + "}").status);
        assertEquals(204, post("/knows", "{\"a\":" + a + ",\"b\":" + b + "}").status);
        assertEquals(204, post("/knows", "{\"a\":" + b + ",\"b\":" + a + "}").status);

        JsonNode ofA = get("/people/" + a + "/contacts").json();
        JsonNode ofB = get("/people/" + b + "/contacts").json();
        assertEquals(1, ofA.size());
        assertEquals(b, ofA.get(0).get("id").asLong());
        assertEquals(1, ofB.size());
        assertEquals(a, ofB.get(0).get("id").asLong());
    }

    @Test
    void knows_rejectsSelfConnection() throws Exception {
        long a = person("A");
        assertEquals(400, post("/knows", "{\"a\":" + a + ",\"b\":" + a + "}").status);
    }

    @Test
    void knows_unknownPerson_returns404() throws Exception {
        long a = person("A");
        Resp resp = post("/knows", "{\"a\":" + a + ",\"b\":99}");
        assertEquals(404, resp.status);
        assertEquals("no person 99", resp.json().get("error").asText());
    }

    @Test
    void knows_rejectsMalformedBodies() throws Exception {
        person("A");
        person("B");
        assertEquals(400, post("/knows", "{\"a\":1}").status);
        assertEquals(400, post("/knows", "{\"b\":1}").status);
        assertEquals(400, post("/knows", "{\"a\":\"1\",\"b\":2}").status);
        assertEquals(400, post("/knows", "{\"a\":1.5,\"b\":2}").status);
        assertEquals(400, post("/knows", "{\"a\":null,\"b\":2}").status);
        assertEquals(400, post("/knows", "garbage").status);
    }

    // ---- GET /people/{id}/contacts --------------------------------------------------

    @Test
    void contacts_ofPersonWithNoContacts_isEmptyArray() throws Exception {
        long a = person("A");
        Resp resp = get("/people/" + a + "/contacts");
        assertEquals(200, resp.status);
        assertTrue(resp.json().isArray());
        assertEquals(0, resp.json().size());
    }

    @Test
    void contacts_unknownPerson_returns404WithMessage() throws Exception {
        Resp resp = get("/people/42/contacts");
        assertEquals(404, resp.status);
        assertEquals("no person 42", resp.json().get("error").asText());
    }

    @Test
    void contacts_nonNumericId_returns400() throws Exception {
        assertEquals(400, get("/people/abc/contacts").status);
    }

    // ---- GET /path ----------------------------------------------------------------

    @Test
    void path_threeHops_isConnected() throws Exception {
        long[] ids = chain("A", "B", "C", "D");
        Resp resp = get("/path?from=" + ids[0] + "&to=" + ids[3]);

        assertEquals(200, resp.status);
        JsonNode json = resp.json();
        assertTrue(json.get("connected").asBoolean());
        assertEquals(3, json.get("hops").asInt());
        assertEquals(4, json.get("path").size());
        assertEquals("A", json.get("path").get(0).get("name").asText());
        assertEquals("D", json.get("path").get(3).get("name").asText());
    }

    @Test
    void path_fourHops_isNotConnected() throws Exception {
        long[] ids = chain("A", "B", "C", "D", "E");
        Resp resp = get("/path?from=" + ids[0] + "&to=" + ids[4]);

        assertEquals(200, resp.status);
        assertFalse(resp.json().get("connected").asBoolean());
        assertFalse(resp.json().has("hops"));
        assertFalse(resp.json().has("path"));
    }

    @Test
    void path_isSymmetric() throws Exception {
        long[] ids = chain("A", "B", "C", "D");
        Resp resp = get("/path?from=" + ids[3] + "&to=" + ids[0]);
        assertTrue(resp.json().get("connected").asBoolean());
        assertEquals(3, resp.json().get("hops").asInt());
        assertEquals("D", resp.json().get("path").get(0).get("name").asText());
    }

    @Test
    void path_samePerson_isZeroHops() throws Exception {
        long a = person("Asha");
        Resp resp = get("/path?from=" + a + "&to=" + a);
        assertEquals(200, resp.status);
        assertTrue(resp.json().get("connected").asBoolean());
        assertEquals(0, resp.json().get("hops").asInt());
        assertEquals(1, resp.json().get("path").size());
        assertEquals("Asha", resp.json().get("path").get(0).get("name").asText());
    }

    @Test
    void path_noConnection_isNotConnected() throws Exception {
        long a = person("A");
        long b = person("B");
        Resp resp = get("/path?from=" + a + "&to=" + b);
        assertEquals(200, resp.status);
        assertFalse(resp.json().get("connected").asBoolean());
    }

    @Test
    void path_prefersShortestRoute() throws Exception {
        // long route A-B-C-D-E (4 hops) plus shortcut A-E (1 hop)
        long[] ids = chain("A", "B", "C", "D", "E");
        knows(ids[0], ids[4]);
        Resp resp = get("/path?from=" + ids[0] + "&to=" + ids[4]);
        assertEquals(1, resp.json().get("hops").asInt());
    }

    @Test
    void path_handlesCycles() throws Exception {
        long a = person("A");
        long b = person("B");
        long c = person("C");
        long d = person("D");
        knows(a, b);
        knows(b, c);
        knows(c, a);
        knows(c, d);
        Resp resp = get("/path?from=" + a + "&to=" + d);
        assertEquals(2, resp.json().get("hops").asInt());
    }

    @Test
    void path_unknownPerson_returns404() throws Exception {
        long a = person("A");
        Resp unknownTo = get("/path?from=" + a + "&to=77");
        assertEquals(404, unknownTo.status);
        assertEquals("no person 77", unknownTo.json().get("error").asText());

        Resp unknownFrom = get("/path?from=77&to=" + a);
        assertEquals(404, unknownFrom.status);
        assertEquals("no person 77", unknownFrom.json().get("error").asText());
    }

    @Test
    void path_missingOrMalformedParams_return400() throws Exception {
        person("A");
        assertEquals(400, get("/path").status);
        assertEquals(400, get("/path?from=1").status);
        assertEquals(400, get("/path?to=1").status);
        assertEquals(400, get("/path?from=x&to=1").status);
        assertEquals(400, get("/path?from=1&to=").status);
    }

    // ---- helpers ------------------------------------------------------------------

    private long person(String name) throws Exception {
        Resp resp = post("/people", JSON.writeValueAsString(Map.of("name", name)));
        assertEquals(201, resp.status);
        return resp.json().get("id").asLong();
    }

    private void knows(long a, long b) throws Exception {
        assertEquals(204, post("/knows", "{\"a\":" + a + ",\"b\":" + b + "}").status);
    }

    /** Creates people with the given names and links them in a line; returns their ids. */
    private long[] chain(String... names) throws Exception {
        long[] ids = new long[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = person(names[i]);
            if (i > 0) {
                knows(ids[i - 1], ids[i]);
            }
        }
        return ids;
    }

    private Resp post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private Resp get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET().build());
    }

    private Resp send(HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(response.statusCode(), response.body());
    }

    private record Resp(int status, String body) {
        JsonNode json() throws Exception {
            return JSON.readTree(body);
        }
    }
}
