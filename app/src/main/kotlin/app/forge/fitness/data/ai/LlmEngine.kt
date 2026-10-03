package app.forge.fitness.data.ai

import android.content.Context
import app.forge.fitness.di.ApplicationScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs the model on the phone with Google's LiteRT-LM. Loading takes a few seconds and a
 * couple of GB of RAM, so it loads on first use and unloads after a minute and a half idle.
 */
@Singleton
class LlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelManager,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var unloadJob: Job? = null

    /** One question, one answer: each call is a fresh conversation, so nothing carries over. */
    suspend fun generate(system: String, prompt: String, maxTokens: Int = 320, temperature: Double = 0.3): String =
        mutex.withLock {
            check(models.isInstalled) { "The AI model isn't installed" }
            unloadJob?.cancel()
            try {
                val e = engine ?: load().also { engine = it }
                withContext(Dispatchers.IO) {
                    val config = ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature),
                        maxOutputToken = maxTokens,
                    )
                    val conversation = e.createConversation(config)
                    try {
                        conversation.sendMessage(prompt).toString().trim()
                    } finally {
                        conversation.close()
                    }
                }
            } finally {
                scheduleUnload()
            }
        }

    /** Frees the model's memory now (e.g. before deleting it). */
    suspend fun unload() = mutex.withLock {
        unloadJob?.cancel()
        engine?.let { runCatching { it.close() } }
        engine = null
    }

    /** GPU first (fast); some phones' drivers refuse, so fall back to the CPU. */
    private suspend fun load(): Engine = withContext(Dispatchers.IO) {
        val path = models.modelFile.absolutePath
        val cache = context.cacheDir.path
        tryInit(EngineConfig(modelPath = path, backend = Backend.GPU(), cacheDir = cache))
            ?: tryInit(EngineConfig(modelPath = path, backend = Backend.CPU(), cacheDir = cache))
            ?: error("The AI model couldn't start on this phone")
    }

    private fun tryInit(config: EngineConfig): Engine? {
        val e = Engine(config)
        return try {
            e.initialize()
            e
        } catch (t: Throwable) {
            runCatching { e.close() }
            null
        }
    }

    private fun scheduleUnload() {
        unloadJob = scope.launch {
            delay(IDLE_MS)
            mutex.withLock {
                engine?.let { runCatching { it.close() } }
                engine = null
            }
        }
    }

    private companion object {
        const val IDLE_MS = 90_000L
    }
}
