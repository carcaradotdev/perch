package dev.carcara.perch.test

import dev.carcara.perch.DeepLinkNavigator
import dev.carcara.perch.DeepLinkTarget
import kotlin.reflect.KClass

/**
 * [DeepLinkNavigator] that records what it was asked to do instead of navigating.
 *
 * One counter and one last-value property per method of the interface, so a test can assert both
 * that a call happened and what it carried. Set [currentTarget] to stand in for the screen the
 * user is on: `DeepLinkManager` replaces rather than stacks when a deep link resolves to the same
 * target type as the current one, and this fake reporting no current target would make that branch
 * untestable.
 */
public class RecordingDeepLinkNavigator : DeepLinkNavigator {
  /**
   * Target the fake reports as sitting at the top of the stack, or null for "nothing on the
   * stack". Set by the test; this fake never updates it on its own, so a test controls exactly
   * what [currentTargetClass] returns.
   */
  public var currentTarget: DeepLinkTarget? = null

  /** Target of the most recent [push], or null if [push] was never called. */
  public var lastPushedRoute: DeepLinkTarget? = null
    private set

  /** Number of [push] calls. */
  public var pushCallCount: Int = 0
    private set

  /** Target of the most recent [replace], or null if [replace] was never called. */
  public var lastReplacedRoute: DeepLinkTarget? = null
    private set

  /** Number of [replace] calls. */
  public var replaceCallCount: Int = 0
    private set

  /** Target of the most recent [setRoot], or null if [setRoot] was never called. */
  public var lastSetRootRoute: DeepLinkTarget? = null
    private set

  /** Number of [setRoot] calls. */
  public var setRootCallCount: Int = 0
    private set

  /** Target of the most recent [present], or null if [present] was never called. */
  public var lastPresentedRoute: DeepLinkTarget? = null
    private set

  /** Number of [present] calls. */
  public var presentCallCount: Int = 0
    private set

  /** Records [target] in [lastPushedRoute] and bumps [pushCallCount]. */
  override fun push(target: DeepLinkTarget) {
    lastPushedRoute = target
    pushCallCount++
  }

  /** Records [target] in [lastReplacedRoute] and bumps [replaceCallCount]. */
  override fun replace(target: DeepLinkTarget) {
    lastReplacedRoute = target
    replaceCallCount++
  }

  /** Records [target] in [lastSetRootRoute] and bumps [setRootCallCount]. */
  override fun setRoot(target: DeepLinkTarget) {
    lastSetRootRoute = target
    setRootCallCount++
  }

  /** Records [target] in [lastPresentedRoute] and bumps [presentCallCount]. */
  override fun present(target: DeepLinkTarget) {
    lastPresentedRoute = target
    presentCallCount++
  }

  /** Class of [currentTarget], or null while no current target is set. */
  override fun currentTargetClass(): KClass<out DeepLinkTarget>? = currentTarget?.let { it::class }
}
