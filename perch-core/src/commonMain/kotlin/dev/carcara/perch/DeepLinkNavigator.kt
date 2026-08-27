package dev.carcara.perch

import kotlin.reflect.KClass

/**
 * Navigation surface [DeepLinkManager] drives once a deep link clears its gates. The app
 * adapts its real navigator to this; every call is made on the main thread.
 */
public interface DeepLinkNavigator {
  /** Push [target] onto the navigation stack. */
  public fun push(target: DeepLinkTarget)

  /** Replace the current top of the stack with [target]. */
  public fun replace(target: DeepLinkTarget)

  /** Reset the navigation stack with [target] as the new root. */
  public fun setRoot(target: DeepLinkTarget)

  /** Present [target] modally. */
  public fun present(target: DeepLinkTarget)

  /**
   * Class of the target currently at the top of the stack, or null when unknown. Used to
   * replace instead of stacking a duplicate when a deep link re-fires the current screen.
   */
  public fun currentTargetClass(): KClass<out DeepLinkTarget>?
}
