package dev.carcara.perch.test

import dev.carcara.perch.DeepLinkNavigator
import dev.carcara.perch.DeepLinkTarget
import kotlin.reflect.KClass

public class RecordingDeepLinkNavigator : DeepLinkNavigator {
  public var lastNavigatedRoute: DeepLinkTarget? = null
    private set
  public var navigateCallCount: Int = 0
    private set
  public var lastReplacedRoute: DeepLinkTarget? = null
    private set
  public var replaceCallCount: Int = 0
    private set
  public var lastSetRootRoute: DeepLinkTarget? = null
    private set
  public var setRootCallCount: Int = 0
    private set
  public var lastPresentedRoute: DeepLinkTarget? = null
    private set
  public var presentCallCount: Int = 0
    private set

  override fun push(target: DeepLinkTarget) {
    lastNavigatedRoute = target
    navigateCallCount++
  }

  override fun replace(target: DeepLinkTarget) {
    lastReplacedRoute = target
    replaceCallCount++
  }

  override fun setRoot(target: DeepLinkTarget) {
    lastSetRootRoute = target
    setRootCallCount++
  }

  override fun present(target: DeepLinkTarget) {
    lastPresentedRoute = target
    presentCallCount++
  }

  override fun currentTargetClass(): KClass<out DeepLinkTarget>? = null
}
