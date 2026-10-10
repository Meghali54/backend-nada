package com.example.network.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response of GET /path. When not connected only {"connected": false} is emitted
 * because null fields are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PathResponse(boolean connected, Integer hops, List<Person> path) {

    public static PathResponse connected(List<Person> path) {
        return new PathResponse(true, path.size() - 1, path);
    }

    public static PathResponse notConnected() {
        return new PathResponse(false, null, null);
    }
}
