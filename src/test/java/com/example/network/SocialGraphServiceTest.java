package com.example.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.network.exception.BadRequestException;
import com.example.network.exception.PersonNotFoundException;
import com.example.network.model.Person;
import com.example.network.repository.InMemoryGraphRepository;
import com.example.network.service.SocialGraphService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fast unit tests for the service layer (no HTTP). */
class SocialGraphServiceTest {

    private SocialGraphService service;

    @BeforeEach
    void setUp() {
        service = new SocialGraphService(new InMemoryGraphRepository());
    }

    @Test
    void boundary_threeHopsFound_fourHopsNot() {
        Person a = service.createPerson("A");
        Person b = service.createPerson("B");
        Person c = service.createPerson("C");
        Person d = service.createPerson("D");
        Person e = service.createPerson("E");
        service.connect(a.id(), b.id());
        service.connect(b.id(), c.id());
        service.connect(c.id(), d.id());
        service.connect(d.id(), e.id());

        Optional<List<Person>> withinLimit = service.shortestPath(a.id(), d.id());
        assertTrue(withinLimit.isPresent());
        assertEquals(List.of(a, b, c, d), withinLimit.get());

        assertTrue(service.shortestPath(a.id(), e.id()).isEmpty());
        // a 3-hop path from the other end is still fine
        assertEquals(List.of(e, d, c, b), service.shortestPath(e.id(), b.id()).orElseThrow());
    }

    @Test
    void createPerson_trimsAndValidates() {
        assertEquals("Asha", service.createPerson("  Asha  ").name());
        assertThrows(BadRequestException.class, () -> service.createPerson(null));
        assertThrows(BadRequestException.class, () -> service.createPerson(" \t\n"));
        assertThrows(BadRequestException.class, () -> service.createPerson("x".repeat(201)));
    }

    @Test
    void connect_validations() {
        Person a = service.createPerson("A");
        assertThrows(BadRequestException.class, () -> service.connect(a.id(), a.id()));
        PersonNotFoundException ex =
                assertThrows(PersonNotFoundException.class, () -> service.connect(a.id(), 9));
        assertEquals("no person 9", ex.getMessage());
    }

    @Test
    void concurrentCreation_yieldsUniqueSequentialIds() throws Exception {
        int threads = 16;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < perThread; i++) {
                    ids.add(service.createPerson("p").id());
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(threads * perThread, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains((long) threads * perThread));
    }
}
