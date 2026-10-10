package com.example.network.repository;

import com.example.network.model.Person;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory implementation.
 * <ul>
 *   <li>ids come from an {@link AtomicLong}, so they are unique and sequential;</li>
 *   <li>people are stored in a {@link ConcurrentHashMap};</li>
 *   <li>adjacency lists are concurrent sets, so re-adding an edge is naturally idempotent.</li>
 * </ul>
 * People are never deleted, so a person that has been observed to exist always exists.
 */
public class InMemoryGraphRepository implements GraphRepository {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Person> people = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> adjacency = new ConcurrentHashMap<>();

    @Override
    public Person savePerson(String name) {
        long id = sequence.incrementAndGet();
        Person person = new Person(id, name);
        adjacency.put(id, ConcurrentHashMap.newKeySet());
        people.put(id, person); // published last: a visible person always has an adjacency set
        return person;
    }

    @Override
    public Optional<Person> findPerson(long id) {
        return Optional.ofNullable(people.get(id));
    }

    @Override
    public void addEdge(long a, long b) {
        Set<Long> aSet = adjacency.get(a);
        Set<Long> bSet = adjacency.get(b);
        if (aSet == null || bSet == null) {
            throw new IllegalArgumentException("unknown person in edge " + a + "-" + b);
        }
        aSet.add(b);
        bSet.add(a);
    }

    @Override
    public Set<Long> neighbours(long id) {
        Set<Long> set = adjacency.get(id);
        return set == null ? Set.of() : new HashSet<>(set);
    }
}
