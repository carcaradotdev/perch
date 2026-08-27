package dev.carcara.perch

/**
 * A navigation destination a deep link can resolve to. The app's route type implements this;
 * Perch only needs to know whether reaching the target requires the user to be authenticated.
 */
public interface DeepLinkTarget {
  public val requiresAuth: Boolean
}
