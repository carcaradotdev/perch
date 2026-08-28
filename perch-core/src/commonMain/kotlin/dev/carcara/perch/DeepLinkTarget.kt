package dev.carcara.perch

/**
 * A navigation destination a deep link can resolve to. The app's route type implements this so
 * [DeepLinkParser] can register and parse it; Perch does not otherwise constrain the shape of a
 * target. What to do with a parsed target — whether and how to navigate to it — is the consuming
 * app's decision.
 */
public interface DeepLinkTarget
