package com.example.network.service;

import com.example.network.exception.BadRequestException;
import com.example.network.exception.PersonNotFoundException;
import com.example.network.model.Person;
import com.example.network.repository.GraphRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Business logic: validation, graph traversal. Free of any HTTP concerns. */
public class SocialGraphService {

    /** Maximum number of hops (edges) a returned path may contain. */
    public static final int MAX_HOPS = 3;
    public static final int MAX_NAME_LENGTH = 200;

    private final GraphRepository repository;

    public SocialGraphService(GraphRepository repository) {
        this.repository = repository;
    }

    public Person createPerson(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name must not be blank");
        }
        String cleaned = name.strip();
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_LENGTH) {
            throw new BadRequestException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return repository.savePerson(cleaned);
    }

    public void connect(long a, long b) {
        if (a == b) {
            throw new BadRequestException("a and b must be different people");
        }
        requirePerson(a);
        requirePerson(b);
        repository.addEdge(a, b);
    }

    public List<Person> contacts(long id) {
        requirePerson(id);
        List<Long> ids = new ArrayList<>(repository.neighbours(id));
        Collections.sort(ids);
        List<Person> result = new ArrayList<>(ids.size());
        for (Long contactId : ids) {
            result.add(requirePerson(contactId));
        }
        return result;
    }

    /**
     * Breadth-first search for the shortest path from {@code from} to {@code to}.
     * Nodes at depth {@link #MAX_HOPS} are not expanded, so the search never explores
     * further than the hop limit.
     *
     * @return the path including both endpoints, or empty if unreachable within the limit
     */
    public Optional<List<Person>> shortestPath(long from, long to) {
        Person start = requirePerson(from);
        requirePerson(to);
        if (from == to) {
            return Optional.of(List.of(start));
        }

        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Integer> depth = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        parent.put(from, null);
        depth.put(from, 0);
        queue.add(from);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            int currentDepth = depth.get(current);
            if (currentDepth >= MAX_HOPS) {
                continue;
            }
            List<Long> next = new ArrayList<>(repository.neighbours(current));
            Collections.sort(next); // deterministic output when several shortest paths exist
            for (Long neighbour : next) {
                if (parent.containsKey(neighbour)) {
                    continue;
                }
                parent.put(neighbour, current);
                depth.put(neighbour, currentDepth + 1);
                if (neighbour == to) {
                    return Optional.of(buildPath(parent, to));
                }
                queue.add(neighbour);
            }
        }
        return Optional.empty();
    }

    private List<Person> buildPath(Map<Long, Long> parent, long target) {
        List<Person> path = new ArrayList<>();
        for (Long at = target; at != null; at = parent.get(at)) {
            path.add(requirePerson(at));
        }
        Collections.reverse(path);
        return path;
    }

    private Person requirePerson(long id) {
        return repository.findPerson(id).orElseThrow(() -> new PersonNotFoundException(id));
    }
}
