package com.example.network.model;

/** Immutable person. Serialises to {"id": 1, "name": "Asha"}. */
public record Person(long id, String name) {}
