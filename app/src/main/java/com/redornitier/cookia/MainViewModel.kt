package com.redornitier.cookia

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.mlc.mlcllm.OpenAIProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class UiState(
    val modelId: String = "Qwen2-1.5B-Instruct-q4f16_1-MLC",
    val installing: Boolean = false,
    val installProgress: InstallProgress? = null,
    val installError: String? = null,
    val installedPath: String? = null,
    val isGenerating: Boolean = false,
    val output: String = "",
    val genError: String? = null,
    val lastGenMs: Long? = null
)

class MainViewModel : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    fun installModel(context: Context) {
        if (_ui.value.installing) return
        _ui.update { it.copy(installing = true, installError = null, installProgress = null) }
        viewModelScope.launch {
            try {
                val dest = ModelInstaller.installIfNeeded(
                    context = context,
                    modelId = _ui.value.modelId
                ) { prog -> _ui.update { s -> s.copy(installProgress = prog) } }
                _ui.update { it.copy(installing = false, installedPath = dest.absolutePath) }
            } catch (e: Exception) {
                _ui.update { it.copy(installing = false, installError = e.message ?: "Error") }
            }
        }
    }

    fun generateWithMLC(app: App, prompt: String) {
        if (_ui.value.isGenerating) return
        _ui.update { it.copy(isGenerating = true, genError = null, output = "") }

        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                val modelId = _ui.value.modelId
                val modelPath = _ui.value.installedPath
                    ?: throw IllegalStateException("Modelo no instalado. Pulsa 'Instalar modelo desde assets'.")

                // 1) Resolver modelLib del JSON de assets (mlc4j empaqueta esto)
                val modelLib = resolveModelLibFromAssets(app, modelId)
                    ?: throw IllegalStateException("No se encontró 'model_lib' para '$modelId' en mlc-app-config.json.")

                // 2) Cargar/recargar el modelo (tu build pide modelPath + modelLib)
                withContext(Dispatchers.IO) { app.engine.reload(modelPath, modelLib) }

                // 3) Construir request OpenAI-style (sin helpers system()/user())
                val messages = listOf(
                    OpenAIProtocol.ChatCompletionMessage(
                        role = OpenAIProtocol.ChatCompletionRole.system,
                        content = "Responde en una frase breve (<=140). Incluye un emoji."
                    ),
                    OpenAIProtocol.ChatCompletionMessage(
                        role = OpenAIProtocol.ChatCompletionRole.user,
                        content = prompt
                    )
                )

                val req = OpenAIProtocol.ChatCompletionRequest(
                    model = modelId,
                    messages = messages,
                    stream = false,
                    temperature = 0.7f,
                    max_tokens = 64
                )

                // 4) Llamada al engine con compat de nombres (chatCompletions / create…)
                val resp: Any = withContext(Dispatchers.IO) { invokeChatCompletions(app, req) }
                val text = extractFirstText(resp)

                val ms = System.currentTimeMillis() - t0
                _ui.update { it.copy(isGenerating = false, output = text, lastGenMs = ms) }
            } catch (e: UnsatisfiedLinkError) {
                _ui.update { it.copy(isGenerating = false, genError = "Native lib no cargada: ${e.message}") }
            } catch (e: Exception) {
                _ui.update { it.copy(isGenerating = false, genError = e.message ?: "Error") }
            }
        }
    }

    /** Busca en assets/mlc-app-config.json el "model_lib" para modelId. */
    private fun resolveModelLibFromAssets(context: Context, modelId: String): String? {
        return try {
            val am = context.assets
            val jsonStr = am.open("mlc-app-config.json").use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
            val root = JSONObject(jsonStr)
            val arr = root.getJSONArray("model_list")
            var lib: String? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("model_id") == modelId) {
                    lib = obj.optString("model_lib", null)
                    if (!lib.isNullOrBlank()) break
                }
            }
            lib
        } catch (_: Exception) {
            null
        }
    }

    /** Intenta llamar a app.engine.<method>(ChatCompletionRequest) con varios nombres comunes. */
    private fun invokeChatCompletions(app: App, req: OpenAIProtocol.ChatCompletionRequest): Any {
        val engine = app.engine
        val klass = engine.javaClass
        val candidates = listOf(
            "chatCompletions",        // algunas builds
            "chatCompletionsCreate",  // espejo de iOS/web: engine.chat.completions.create
            "createChatCompletions"   // otras builds
        )
        var lastErr: Throwable? = null
        for (name in candidates) {
            try {
                val m = klass.getMethod(name, req.javaClass)
                return m.invoke(engine, req)!!
            } catch (t: Throwable) {
                lastErr = t
            }
        }
        throw NoSuchMethodError("No encontré método chat completions en MLCEngine (${candidates.joinToString()}). Causa: ${lastErr?.message}")
    }

    /** Extrae texto del response (message.content o delta.content). */
    @Suppress("UNCHECKED_CAST")
    private fun extractFirstText(resp: Any): String {
        // Try: resp.choices[0].message.content
        try {
            val choices = resp.javaClass.getMethod("getChoices").invoke(resp) as? List<*>
            val first = choices?.firstOrNull() ?: return ""
            val msg = first.javaClass.getMethod("getMessage").invoke(first)
            val content = msg.javaClass.getMethod("getContent").invoke(msg) as? String
            if (!content.isNullOrBlank()) return content
        } catch (_: Throwable) {}

        // Try streaming shape: resp.choices[0].delta.content (o content.asText())
        try {
            val choices = resp.javaClass.getMethod("getChoices").invoke(resp) as? List<*>
            val first = choices?.firstOrNull() ?: return ""
            val delta = first.javaClass.getMethod("getDelta").invoke(first)
            val raw = delta?.javaClass?.getMethod("getContent")?.invoke(delta)
            when (raw) {
                is String -> return raw
                else -> {
                    val asText = raw?.javaClass?.methods?.firstOrNull { it.name == "asText" }?.invoke(raw)
                    if (asText is String) return asText
                }
            }
        } catch (_: Throwable) {}

        return ""
    }
}
