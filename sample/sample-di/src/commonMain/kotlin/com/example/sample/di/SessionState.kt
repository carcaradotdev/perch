package com.example.sample.di

/**
 * Whether anyone is signed in, for the handlers that gate on it.
 *
 * Nothing in the sample authenticates. What matters is that a feature's handler can ask for this in
 * its constructor and the graph supplies it, with no shell passing it down by hand.
 */
public interface SessionState {
  public val signedIn: Boolean
}
