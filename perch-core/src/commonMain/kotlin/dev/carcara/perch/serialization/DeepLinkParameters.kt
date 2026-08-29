package dev.carcara.perch.serialization

/**
 * The name-to-values multimap that path parameters and query parameters travel in, and the only
 * thing [DeepLinkFormat] and the decoders read from.
 *
 * A name may carry several values, which is how a `{name...}` tailcard and a repeated query
 * parameter are represented. Insertion order is preserved for both names and values, so a URL
 * built from these parameters comes out in a stable order.
 */
internal class DeepLinkParameters private constructor(
  private val values: Map<String, List<String>>,
) {

  /** The first value under [name], or null when the name is absent. */
  operator fun get(name: String): String? = values[name]?.firstOrNull()

  /** Every value under [name] in order, or null when the name is absent. */
  fun getAll(name: String): List<String>? = values[name]

  fun contains(name: String): Boolean = values.containsKey(name)

  /** Every name-to-values pair, in insertion order. */
  fun entries(): List<Pair<String, List<String>>> = values.map { it.key to it.value }

  /** The parameters whose name satisfies [predicate]. */
  fun filterNames(predicate: (String) -> Boolean): DeepLinkParameters =
    DeepLinkParameters(values.filterKeys(predicate))

  class Builder {
    private val values = mutableMapOf<String, MutableList<String>>()

    fun append(name: String, value: String) {
      values.getOrPut(name) { mutableListOf() }.add(value)
    }

    fun appendAll(other: DeepLinkParameters) {
      other.entries().forEach { (name, entryValues) -> entryValues.forEach { append(name, it) } }
    }

    fun build(): DeepLinkParameters = DeepLinkParameters(values.mapValues { it.value.toList() })
  }

  companion object {
    val Empty: DeepLinkParameters = DeepLinkParameters(emptyMap())

    /**
     * Inline so a caller may return from the enclosing function inside [block] — which is how
     * pattern matching abandons a half-built parameter set the moment a segment fails.
     */
    inline fun build(block: Builder.() -> Unit): DeepLinkParameters = Builder().apply(block).build()
  }
}
