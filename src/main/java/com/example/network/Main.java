package com.example.network;

import com.example.network.controller.ApiServer;
import com.example.network.repository.InMemoryGraphRepository;
import com.example.network.service.SocialGraphService;
import io.javalin.Javalin;

/** Application entry point. Port is read from the PORT env var (default 7070). */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        int port = resolvePort(System.getenv("PORT"));
        SocialGraphService service = new SocialGraphService(new InMemoryGraphRepository());
        Javalin app = ApiServer.create(service);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        app.start(port);
    }

    private static int resolvePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return 7070;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid PORT value: " + raw, e);
        }
    }
}
