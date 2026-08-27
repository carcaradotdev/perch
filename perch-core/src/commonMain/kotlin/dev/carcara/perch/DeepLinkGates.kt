package dev.carcara.perch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication gate for deep-link dispatch. [DeepLinkManager] holds an auth-required
 * target pending until [isAuthenticated] is true.
 */
public interface DeepLinkAuthGate {
  public val isAuthenticated: StateFlow<Boolean>

  /** Gate for an app with no sign-in. Every auth-required target passes immediately. */
  public companion object AlwaysAuthenticated : DeepLinkAuthGate {
    override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true)
  }
}

/**
 * App-lock gate for deep-link dispatch. [DeepLinkManager] holds an auth-required target
 * pending until [isUnlocked] is true.
 */
public interface DeepLinkLockGate {
  public val isUnlocked: StateFlow<Boolean>

  /** Gate for an app with no lock screen. Every auth-required target passes immediately. */
  public companion object AlwaysUnlocked : DeepLinkLockGate {
    override val isUnlocked: StateFlow<Boolean> = MutableStateFlow(true)
  }
}
