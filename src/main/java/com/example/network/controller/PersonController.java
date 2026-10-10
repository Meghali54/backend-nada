package com.example.network.controller;

import com.example.network.exception.BadRequestException;
import com.example.network.model.PathResponse;
import com.example.network.model.Person;
import com.example.network.service.SocialGraphService;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** HTTP layer: parses/validates input, delegates to the service, writes JSON. */
public class PersonController {

    private final SocialGraphService service;

    public PersonController(SocialGraphService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.post("/people", this::createPerson);
        app.post("/knows", this::connect);
        app.get("/people/{id}/contacts", this::contacts);
        app.get("/path", this::path);
    }

    private void createPerson(Context ctx) {
        JsonNode body = readJsonObject(ctx);
        JsonNode name = body.get("name");
        if (name == null || !name.isTextual()) {
            throw new BadRequestException("name is required and must be a string");
        }
        Person person = service.createPerson(name.asText());
        ApiServer.writeJson(ctx, 201, person);
    }

    private void connect(Context ctx) {
        JsonNode body = readJsonObject(ctx);
        long a = requiredId(body, "a");
        long b = requiredId(body, "b");
        service.connect(a, b);
        ctx.status(204);
    }

    private void contacts(Context ctx) {
        long id = parseId(ctx.pathParam("id"), "id");
        List<Person> contacts = service.contacts(id);
        ApiServer.writeJson(ctx, 200, contacts);
    }

    private void path(Context ctx) {
        long from = parseId(ctx.queryParam("from"), "from");
        long to = parseId(ctx.queryParam("to"), "to");
        Optional<List<Person>> path = service.shortestPath(from, to);
        PathResponse response = path.map(PathResponse::connected).orElseGet(PathResponse::notConnected);
        ApiServer.writeJson(ctx, 200, response);
    }

    // ---- helpers -----------------------------------------------------------------

    private static JsonNode readJsonObject(Context ctx) {
        byte[] raw = ctx.bodyAsBytes();
        if (raw.length == 0) {
            throw new BadRequestException("request body is required");
        }
        JsonNode node;
        try {
            node = ApiServer.MAPPER.readTree(raw); // auto-detects UTF-8
        } catch (IOException e) {
            throw new BadRequestException("invalid JSON");
        }
        if (node == null || !node.isObject()) {
            throw new BadRequestException("request body must be a JSON object");
        }
        return node;
    }

    private static long requiredId(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new BadRequestException(field + " is required and must be an integer");
        }
        return value.asLong();
    }

    private static long parseId(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("parameter '" + field + "' is required");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("'" + field + "' must be an integer");
        }
    }
}
