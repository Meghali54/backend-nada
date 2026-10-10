package com.example.network.controller;

import com.example.network.exception.ApiException;
import com.example.network.service.SocialGraphService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the Javalin application: routes + uniform error handling. */
public final class ApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);
    static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiServer() {}

    public static Javalin create(SocialGraphService service) {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);

        new PersonController(service).register(app);

        app.exception(ApiException.class, (e, ctx) -> writeJson(ctx, e.status(), Map.of("error", e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled error", e);
            writeJson(ctx, 500, Map.of("error", "internal server error"));
        });
        return app;
    }

    /** Serialises explicitly as UTF-8 so non-ASCII names (e.g. Hindi) round-trip safely. */
    static void writeJson(Context ctx, int status, Object body) {
        try {
            ctx.status(status)
               .contentType("application/json; charset=utf-8")
               .result(MAPPER.writeValueAsBytes(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise response", e);
        }
    }
}
