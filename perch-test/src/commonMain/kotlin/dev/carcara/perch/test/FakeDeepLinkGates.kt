package dev.carcara.perch.test

import dev.carcara.perch.DeepLinkAuthGate
import dev.carcara.perch.DeepLinkLockGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [DeepLinkAuthGate] a test drives by hand, starting signed out.
 *
 * Both transitions are available, so a test can express a session ending as well as one starting:
 * a deep link queued behind [signOut] must stay queued.
 */
public class FakeDeepLinkAuthGate : DeepLinkAuthGate {
  private val _isAuthenticated = MutableStateFlow(false)

  /** False until [authenticate] is called, and false again after [signOut]. */
  override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  /** Opens the gate, as a successful sign-in would. */
  public fun authenticate() {
    _isAuthenticated.value = true
  }

  /** Closes the gate again, as a sign-out or an expired session would. */
  public fun signOut() {
    _isAuthenticated.value = false
  }
}

/**
 * [DeepLinkLockGate] a test drives by hand, starting locked.
 *
 * Both transitions are available, so a test can express the lock screen returning as well as being
 * dismissed: a deep link queued behind [lock] must stay queued.
 */
public class FakeDeepLinkLockGate : DeepLinkLockGate {
  private val _isUnlocked = MutableStateFlow(false)

  /** False until [unlock] is called, and false again after [lock]. */
  override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

  /** Opens the gate, as dismissing the lock screen would. */
  public fun unlock() {
    _isUnlocked.value = true
  }

  /** Closes the gate again, as the lock screen reappearing would. */
  public fun lock() {
    _isUnlocked.value = false
  }
}
