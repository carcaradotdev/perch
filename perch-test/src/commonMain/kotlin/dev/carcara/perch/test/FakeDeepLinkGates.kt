package dev.carcara.perch.test

import dev.carcara.perch.DeepLinkAuthGate
import dev.carcara.perch.DeepLinkLockGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class FakeDeepLinkAuthGate : DeepLinkAuthGate {
  private val _isAuthenticated = MutableStateFlow(false)
  override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  public fun setSuccess() {
    _isAuthenticated.value = true
  }
}

public class FakeDeepLinkLockGate : DeepLinkLockGate {
  private val _isUnlocked = MutableStateFlow(false)
  override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

  public fun unlock() {
    _isUnlocked.value = true
  }
}
