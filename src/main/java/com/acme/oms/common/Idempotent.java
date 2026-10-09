package com.acme.oms.common;

/** Result of an idempotent operation; {@code replayed} is true when a previous result was returned. */
public record Idempotent<T>(T value, boolean replayed) {}
