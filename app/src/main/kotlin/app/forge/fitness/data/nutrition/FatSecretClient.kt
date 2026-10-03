package app.forge.fitness.data.nutrition

import app.forge.domain.nutrition.FatSecret
import app.forge.domain.nutrition.FatSecretHit
import app.forge.domain.nutrition.FoodInfo
import app.forge.fitness.data.prefs.UserPreferencesRepository
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

sealed interface FsResult<out T> {
    data class Ok<T>(val value: T) : FsResult<T>
    /** No key entered yet (Settings → Food sources). */
    data object NotSetUp : FsResult<Nothing>
    data class Failed(val message: String) : FsResult<Nothing>
}

/**
 * FatSecret Platform API with your own free key: 2M+ foods including McDonald's, KFC
 * and other chains and big brands. The free tier is FatSecret's US database, so some
 * Australian items differ or are missing (the web lookup covers those).
 * Only the search words are sent, never anything about you.
 */
@Singleton
class FatSecretClient @Inject constructor(
    private val preferences: UserPreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var token: String? = null
    private var tokenFor: String? = null
    private var tokenExpiresAt = 0L

    suspend fun isSetUp(): Boolean = keys() != null

    suspend fun search(query: String): FsResult<List<FatSecretHit>> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return call("method=foods.search&search_expression=$q&max_results=25") { FatSecret.parseSearch(it) }
    }

    suspend fun food(id: String): FsResult<FoodInfo> {
        for (method in listOf("food.get.v4", "food.get.v2")) {
            when (val r = call("method=$method&food_id=$id") { FatSecret.parseFood(it) }) {
                is FsResult.Ok -> r.value?.let { return FsResult.Ok(it) }
                is FsResult.Failed -> if (method == "food.get.v2") return r
                FsResult.NotSetUp -> return FsResult.NotSetUp
            }
        }
        return FsResult.Failed("FatSecret didn't have details for that food")
    }

    /** Checks a key before saving it. Null when it works, otherwise what's wrong. */
    suspend fun test(clientId: String, secret: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val t = fetchToken(clientId.trim(), secret.trim()) ?: return@runCatching "FatSecret didn't accept that Client ID and Secret."
            val r = Http.get("$API?method=foods.search&search_expression=banana&max_results=1&format=json", mapOf("Authorization" to "Bearer $t"))
            FatSecret.error(r.body)?.let { explain(it.code, it.message) }
        }.getOrElse { offline(it) }
    }

    private suspend fun keys(): Pair<String, String>? {
        val p = preferences.preferences.first()
        val id = p.fatSecretClientId ?: return null
        val secret = p.fatSecretSecret ?: return null
        return id to secret
    }

    private suspend fun <T> call(params: String, parse: (String) -> T): FsResult<T> = withContext(Dispatchers.IO) {
        val (id, secret) = keys() ?: return@withContext FsResult.NotSetUp
        runCatching {
            var t = token(id, secret) ?: return@runCatching FsResult.Failed("FatSecret didn't accept your key. Check it in Settings → Food sources.")
            var r = Http.get("$API?$params&format=json", mapOf("Authorization" to "Bearer $t"))
            var error = FatSecret.error(r.body)
            if (error != null && error.code in TOKEN_ERRORS) {
                // Expired or revoked token: get a new one once.
                lock.withLock { token = null }
                t = token(id, secret) ?: return@runCatching FsResult.Failed("FatSecret didn't accept your key.")
                r = Http.get("$API?$params&format=json", mapOf("Authorization" to "Bearer $t"))
                error = FatSecret.error(r.body)
            }
            when {
                error != null -> FsResult.Failed(explain(error.code, error.message))
                r.code !in 200..299 -> FsResult.Failed("FatSecret returned an error (${r.code})")
                else -> FsResult.Ok(parse(r.body))
            }
        }.getOrElse { FsResult.Failed(offline(it)) }
    }

    private suspend fun token(id: String, secret: String): String? = lock.withLock {
        val now = System.currentTimeMillis()
        if (token != null && tokenFor == id && now < tokenExpiresAt) return@withLock token
        fetchToken(id, secret)?.also { token = it; tokenFor = id }
    }

    /** OAuth 2.0 client credentials. */
    private fun fetchToken(id: String, secret: String): String? {
        val basic = Base64.getEncoder().encodeToString("$id:$secret".toByteArray())
        val r = Http.postForm(TOKEN_URL, "grant_type=client_credentials&scope=basic", mapOf("Authorization" to "Basic $basic"))
        if (r.code !in 200..299) return null
        val root = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull() ?: return null
        val access = (root["access_token"] as? JsonPrimitive)?.contentOrNull ?: return null
        val seconds = (root["expires_in"] as? JsonPrimitive)?.longOrNull ?: 3_600
        tokenExpiresAt = System.currentTimeMillis() + (seconds - 60).coerceAtLeast(60) * 1_000
        return access
    }

    private fun explain(code: Int, message: String) = when (code) {
        21 -> "FatSecret blocked this phone's internet address. In your FatSecret account, open IP Restrictions and " +
            "add 0.0.0.0/0 (allow all), then wait a few minutes."
        in TOKEN_ERRORS -> "FatSecret didn't accept your key. Check it in Settings → Food sources."
        else -> "FatSecret: $message"
    }

    private fun offline(e: Throwable) =
        if (e is IOException) "Couldn't reach FatSecret. Check your internet connection." else (e.message ?: "FatSecret lookup failed")

    private companion object {
        const val TOKEN_URL = "https://oauth.fatsecret.com/connect/token"
        const val API = "https://platform.fatsecret.com/rest/server.api"
        val TOKEN_ERRORS = setOf(13, 14)
    }
}
