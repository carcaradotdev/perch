package dev.carcara.perch

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinkParserTest {

  private fun parser() = DeepLinkParser(schemes = setOf("acme"))

  @Test
  fun `parse simple path with required parameter`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("acme://payments/abc123")

    assertIs<PaymentDeepLink>(result)
    assertEquals("abc123", result.id)
  }

  @Test
  fun `parse path without scheme`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("payments/abc123")

    assertIs<PaymentDeepLink>(result)
    assertEquals("abc123", result.id)
  }

  @Test
  fun `parse path with leading slash`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("/payments/abc123")

    assertIs<PaymentDeepLink>(result)
    assertEquals("abc123", result.id)
  }

  @Test
  fun `parse nested path`() {
    val subject = parser()
    subject.register<TransactionDetailDeepLink>()

    val result = subject.parse("acme://transactions/tx123/details")

    assertIs<TransactionDetailDeepLink>(result)
    assertEquals("tx123", result.id)
  }

  @Test
  fun `parse path with query parameters`() {
    val subject = parser()
    subject.register<SearchDeepLink>()

    val result = subject.parse("acme://search?query=coffee&limit=10")

    assertIs<SearchDeepLink>(result)
    assertEquals("coffee", result.query)
    assertEquals(10, result.limit)
  }

  @Test
  fun `parse path with optional parameter present`() {
    val subject = parser()
    subject.register<ProfileDeepLink>()

    val result = subject.parse("acme://profile/user123")

    assertIs<ProfileDeepLink>(result)
    assertEquals("user123", result.userId)
  }

  @Test
  fun `parse path with optional parameter missing`() {
    val subject = parser()
    subject.register<ProfileDeepLink>()

    val result = subject.parse("acme://profile")

    assertIs<ProfileDeepLink>(result)
    assertNull(result.userId)
  }

  @Test
  fun `parse path with multiple parameters`() {
    val subject = parser()
    subject.register<OrderItemDeepLink>()

    val result = subject.parse("acme://orders/order123/items/item456")

    assertIs<OrderItemDeepLink>(result)
    assertEquals("order123", result.orderId)
    assertEquals("item456", result.itemId)
  }

  @Test
  fun `parse returns null for unregistered route`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("acme://unknown/path")

    assertNull(result)
  }

  @Test
  fun `register skips a route with no DeepLink annotation instead of crashing`() {
    // A serializable Route whose @DeepLink was removed but is still registered via a stale
    // generated registration. encodeToPathPattern throws on it; registration must not crash.
    val subject = parser()
    subject.register<RouteWithoutAnnotation>()

    assertNull(subject.parse("acme://anything"))
  }

  @Test
  fun `a bad route does not block registration of valid routes`() {
    val subject = parser()
    subject.register<RouteWithoutAnnotation>()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("acme://payments/abc123")

    assertIs<PaymentDeepLink>(result)
    assertEquals("abc123", result.id)
  }

  @Test
  fun `parse returns null for partial path match`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("acme://payments")

    assertNull(result)
  }

  @Test
  fun `parse returns null for path with extra segments`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    val result = subject.parse("acme://payments/abc123/extra")

    assertNull(result)
  }

  @Test
  fun `parse first matching route when multiple registered`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()
    subject.register<TransactionDetailDeepLink>()

    val paymentResult = subject.parse("acme://payments/abc123")
    val transactionResult = subject.parse("acme://transactions/tx123/details")

    assertIs<PaymentDeepLink>(paymentResult)
    assertIs<TransactionDetailDeepLink>(transactionResult)
  }

  @Test
  fun `parse constant only path`() {
    val subject = parser()
    subject.register<HomeDeepLink>()

    val result = subject.parse("acme://home")

    assertIs<HomeDeepLink>(result)
  }

  @Test
  fun `toUrl generates correct URL with parameter`() {
    val url = parser().toUrl(PaymentDeepLink("abc123"))

    assertEquals("acme://payments/abc123", url)
  }

  @Test
  fun `toUrl generates correct URL for nested path`() {
    val url = parser().toUrl(TransactionDetailDeepLink("tx123"))

    assertEquals("acme://transactions/tx123/details", url)
  }

  @Test
  fun `toUrl generates correct URL with multiple parameters`() {
    val url = parser().toUrl(OrderItemDeepLink("order123", "item456"))

    assertEquals("acme://orders/order123/items/item456", url)
  }

  @Test
  fun `toUrl generates correct URL for constant path`() {
    val url = parser().toUrl(HomeDeepLink())

    assertEquals("acme://home", url)
  }

  @Test
  fun `parsing against an empty parser reports it once`() {
    val recorded = mutableListOf<String>()
    val subject = DeepLinkParser(
      schemes = setOf("acme"),
      logger = DeepLinkLogger { message, _ -> recorded += message },
    )

    subject.parse("acme://payments/abc123")
    subject.parse("acme://home")

    assertEquals(1, recorded.size)
    assertTrue(recorded.single().contains("no routes registered"))
  }

  @Test
  fun `a parser with routes reports nothing`() {
    val recorded = mutableListOf<String>()
    val subject = DeepLinkParser(
      schemes = setOf("acme"),
      logger = DeepLinkLogger { message, _ -> recorded += message },
    )
    subject.register<PaymentDeepLink>()

    subject.parse("acme://nothing/matches/this")

    assertTrue(recorded.isEmpty(), recorded.toString())
  }

  @Test
  fun `narrowing the result keeps a when over a sealed route type exhaustive`() {
    // Perch imposes no supertype, so `parse` hands back Any?. An app that groups its routes under
    // a sealed type of its own narrows once and keeps the exhaustive `when`, with no `else`. This
    // test compiling is the assertion.
    val subject = parser()
    subject.register<SealedPayment>()
    subject.register<SealedHome>()

    val label = when (val route = subject.parse("acme://sealed/payments/abc123") as? SealedRoute) {
      is SealedPayment -> route.id
      is SealedHome -> "home"
      null -> "unmatched"
    }

    assertEquals("abc123", label)
  }

  @Test
  fun `a route the parser knows but the app's own type does not cover narrows to null`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()

    assertNull(subject.parse("acme://payments/abc123") as? SealedRoute)
  }

  @Test
  fun `roundtrip - parse generated URL`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()
    val original = PaymentDeepLink("abc123")

    val url = subject.toUrl(original)
    val parsed = subject.parse(url)

    assertIs<PaymentDeepLink>(parsed)
    assertEquals(original.id, parsed.id)
  }

  @Test
  fun `roundtrip of a value that has to be percent-encoded`() {
    val subject = parser()
    subject.register<PaymentDeepLink>()
    val original = PaymentDeepLink("a b/c é")

    val url = subject.toUrl(original)
    val parsed = subject.parse(url)

    // The slash is encoded, so it stays inside the id instead of splitting it into two segments.
    assertEquals("acme://payments/a%20b%2Fc%20%C3%A9", url)
    assertIs<PaymentDeepLink>(parsed)
    assertEquals(original.id, parsed.id)
  }

  @Test
  fun `roundtrip of a query value that has to be percent-encoded`() {
    val subject = parser()
    subject.register<SearchDeepLink>()
    val original = SearchDeepLink(query = "a&b=c", limit = 5)

    val parsed = subject.parse(subject.toUrl(original))

    assertIs<SearchDeepLink>(parsed)
    assertEquals(original.query, parsed.query)
    assertEquals(5, parsed.limit)
  }

  @Test
  fun `roundtrip with multiple parameters`() {
    val subject = parser()
    subject.register<OrderItemDeepLink>()
    val original = OrderItemDeepLink("order123", "item456")

    val url = subject.toUrl(original)
    val parsed = subject.parse(url)

    assertIs<OrderItemDeepLink>(parsed)
    assertEquals(original.orderId, parsed.orderId)
    assertEquals(original.itemId, parsed.itemId)
  }

  // =============================================================================
  // Q1: Trailing slash behaviour - are /feature/list and /feature/list/ the same?
  // =============================================================================

  @Test
  fun `trailing slash - route without slash matches URL without slash`() {
    val subject = parser()
    subject.register<FeatureListNoSlash>()

    val result = subject.parse("acme://feature/list")

    assertIs<FeatureListNoSlash>(result)
  }

  @Test
  fun `trailing slash - route without slash matches URL with slash`() {
    val subject = parser()
    subject.register<FeatureListNoSlash>()

    val result = subject.parse("acme://feature/list/")

    assertIs<FeatureListNoSlash>(result)
  }

  @Test
  fun `trailing slash - route with slash matches URL without slash`() {
    val subject = parser()
    subject.register<FeatureListWithSlash>()

    val result = subject.parse("acme://feature/list")

    assertIs<FeatureListWithSlash>(result)
  }

  @Test
  fun `trailing slash - collision detection treats them as same`() {
    val subject = parser()
    subject.register<FeatureListNoSlash>()

    var collisionDetected = false
    try {
      subject.register<FeatureListWithSlash>()
    } catch (expected: DeepLinkCollisionException) {
      collisionDetected = true
    }
    assertTrue(collisionDetected, "Expected collision between /feature/list and /feature/list/")
  }

  @Test
  fun `pattern collision - static vs parameter segment`() {
    val subject = parser()
    subject.register<FeatureListNoSlash>() // /feature/list

    // /feature/{id} conflicts because "list" could be matched by {id}.
    var collisionDetected = false
    try {
      subject.register<FeatureByIdDeepLink>()
    } catch (expected: DeepLinkCollisionException) {
      collisionDetected = true
    }
    assertTrue(collisionDetected, "Expected collision between /feature/list and /feature/{id}")
  }

  @Test
  fun `pattern collision - two different parameter names`() {
    val subject = parser()
    subject.register<FeatureByIdDeepLink>() // /feature/{id}

    // /feature/{name} conflicts because the two are structurally identical.
    var collisionDetected = false
    try {
      subject.register<FeatureByNameDeepLink>()
    } catch (expected: DeepLinkCollisionException) {
      collisionDetected = true
    }
    assertTrue(collisionDetected, "Expected collision between /feature/{id} and /feature/{name}")
  }

  @Test
  fun `no collision - different static segments`() {
    val subject = parser()
    subject.register<FeatureListNoSlash>() // /feature/list

    subject.register<FeatureDetailsDeepLink>() // /feature/details

    assertIs<FeatureDetailsDeepLink>(subject.parse("acme://feature/details"))
  }

  @Test
  fun `no collision - different segment count`() {
    val subject = parser()
    subject.register<FeatureListNoSlash>() // /feature/list

    subject.register<FeatureListDetailsDeepLink>() // /feature/list/details

    assertIs<FeatureListDetailsDeepLink>(subject.parse("acme://feature/list/details"))
  }

  // =============================================================================
  // Q2: SerialName annotation - can we map property name to different path param?
  // =============================================================================

  @Test
  fun `serialName - property with different name than path parameter`() {
    val subject = parser()
    subject.register<TransferDeepLink>()

    val result = subject.parse("acme://transfer/abc123")

    assertIs<TransferDeepLink>(result)
    assertEquals("abc123", result.transferId)
  }

  @Test
  fun `serialName - toUrl generates correct path with mapped parameter`() {
    val deepLink = TransferDeepLink(transferId = "xyz789")

    val url = parser().toUrl(deepLink)

    assertEquals("acme://transfer/xyz789", url)
  }

  @Test
  fun `serialName - roundtrip with mapped parameter name`() {
    val subject = parser()
    subject.register<TransferDeepLink>()
    val original = TransferDeepLink(transferId = "test123")

    val url = subject.toUrl(original)
    val parsed = subject.parse(url)

    assertIs<TransferDeepLink>(parsed)
    assertEquals(original.transferId, parsed.transferId)
  }

  // =============================================================================
  // Q3: Nested resources with parent references (Ktor style)
  // =============================================================================

  @Test
  fun `nested resources - base route with default sort`() {
    val subject = parser()
    subject.register<ArticlesDeepLink>()

    val result = subject.parse("acme://articles")

    assertIs<ArticlesDeepLink>(result)
    assertEquals("new", result.sort)
  }

  @Test
  fun `nested resources - base route with custom sort query param`() {
    val subject = parser()
    subject.register<ArticlesDeepLink>()

    val result = subject.parse("acme://articles?sort=popular")

    assertIs<ArticlesDeepLink>(result)
    assertEquals("popular", result.sort)
  }

  @Test
  fun `nested resources - child route with parent reference`() {
    val subject = parser()
    subject.register<ArticlesDeepLink.New>()

    // Ktor nested resources: /articles/new
    assertIs<ArticlesDeepLink.New>(subject.parse("acme://articles/new"))
  }

  @Test
  fun `nested resources - child route with id parameter`() {
    val subject = parser()
    subject.register<ArticlesDeepLink.ById>()

    // Ktor nested resources: /articles/{id}
    val result = subject.parse("acme://articles/123")

    assertIs<ArticlesDeepLink.ById>(result)
    assertEquals(123L, result.id)
  }

  @Test
  fun `nested resources - deeply nested route`() {
    val subject = parser()
    subject.register<ArticlesDeepLink.ById.Edit>()

    // Ktor nested resources: /articles/{id}/edit
    assertIs<ArticlesDeepLink.ById.Edit>(subject.parse("acme://articles/123/edit"))
  }

  // =============================================================================
  // Q4: Query parameters with defaults
  // =============================================================================

  @Test
  fun `query params - all defaults used when no params provided`() {
    val subject = parser()
    subject.register<ItemsDeepLink>()

    val result = subject.parse("acme://items")

    assertIs<ItemsDeepLink>(result)
    assertEquals(1, result.page)
    assertEquals(20, result.limit)
    assertNull(result.filter)
  }

  @Test
  fun `query params - partial params override defaults`() {
    val subject = parser()
    subject.register<ItemsDeepLink>()

    val result = subject.parse("acme://items?page=5")

    assertIs<ItemsDeepLink>(result)
    assertEquals(5, result.page)
    assertEquals(20, result.limit) // default
    assertNull(result.filter)
  }

  @Test
  fun `query params - all params provided`() {
    val subject = parser()
    subject.register<ItemsDeepLink>()

    val result = subject.parse("acme://items?page=3&limit=50&filter=active")

    assertIs<ItemsDeepLink>(result)
    assertEquals(3, result.page)
    assertEquals(50, result.limit)
    assertEquals("active", result.filter)
  }
}

/** An app's own route type, which Perch neither requires nor knows about. */
internal sealed interface SealedRoute

@DeepLink("/sealed/payments/{id}")
internal data class SealedPayment(val id: String) : SealedRoute

@DeepLink("/sealed/home")
internal data object SealedHome : SealedRoute

// A serializable deep-link target with no @DeepLink — the shape a stale generated registration
// produces when a route's @DeepLink is removed but its module's manifest still lists it.
@Serializable
data object RouteWithoutAnnotation

@DeepLink("/payments/{id}")
data class PaymentDeepLink(val id: String)

@DeepLink("/transactions/{id}/details")
data class TransactionDetailDeepLink(val id: String)

@DeepLink("/search")
data class SearchDeepLink(
  val query: String,
  val limit: Int = 20,
)

@DeepLink("/profile/{userId?}")
data class ProfileDeepLink(val userId: String? = null)

@DeepLink("/orders/{orderId}/items/{itemId}")
data class OrderItemDeepLink(
  val orderId: String,
  val itemId: String,
)

@DeepLink("/home")
class HomeDeepLink

// =============================================================================
// Targets for edge cases and advanced features
// =============================================================================

@DeepLink("/feature/list")
data object FeatureListNoSlash

@DeepLink("/feature/list/")
data object FeatureListWithSlash

@DeepLink("/feature/{id}")
data class FeatureByIdDeepLink(val id: String)

@DeepLink("/feature/{name}")
data class FeatureByNameDeepLink(val name: String)

@DeepLink("/feature/details")
data object FeatureDetailsDeepLink

@DeepLink("/feature/list/details")
data object FeatureListDetailsDeepLink

@DeepLink("/transfer/{id}")
data class TransferDeepLink(
  @kotlinx.serialization.SerialName("id")
  val transferId: String,
)

@DeepLink("/articles")
data class ArticlesDeepLink(val sort: String? = "new") {
  @DeepLink("new")
  data class New(val parent: ArticlesDeepLink = ArticlesDeepLink())

  @DeepLink("{id}")
  data class ById(
    val parent: ArticlesDeepLink = ArticlesDeepLink(),
    val id: Long,
  ) {
    @DeepLink("edit")
    data class Edit(val parent: ById)
  }
}

@DeepLink("/items")
data class ItemsDeepLink(
  val page: Int = 1,
  val limit: Int = 20,
  val filter: String? = null,
)
