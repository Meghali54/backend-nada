package com.example.network.repository;

import com.example.network.model.Person;
import java.util.Optional;
import java.util.Set;

/** Storage abstraction so the in-memory store can be swapped for a real database later. */
public interface GraphRepository {

    /** Persists a new person and assigns the next sequential id (starting at 1). */
    Person savePerson(String name);

    Optional<Person> findPerson(long id);

    /** Adds an undirected edge. Idempotent. Both ids must already exist. */
    void addEdge(long a, long b);

    /** Snapshot of the ids directly connected to {@code id}; empty if none. */
    Set<Long> neighbours(long id);
}
