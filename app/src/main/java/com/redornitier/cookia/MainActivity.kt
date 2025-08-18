package com.redornitier.cookia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App

        setContent {
            MaterialTheme {
                val ui by vm.ui.collectAsState()
                var prompt by remember { mutableStateOf("Dame una galleta absurda sobre baterías 🔋🍪") }
                val driver = remember { app.engine.driver }

                Scaffold(
                    topBar = { CenterAlignedTopAppBar(title = { Text("BatteryCookie — MLC") }) }
                ) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Modelo: ${ui.modelId}", style = MaterialTheme.typography.titleMedium)
                        Text("Driver: $driver")

                        Button(
                            onClick = { vm.installModel(applicationContext) },
                            enabled = !ui.installing
                        ) {
                            if (ui.installing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Copiando…")
                                }
                            } else {
                                Text("Instalar modelo desde assets")
                            }
                        }

                        when {
                            ui.installing && ui.installProgress != null -> {
                                val p = ui.installProgress
                                if (p != null) {
                                    Text("Progreso: ${p.copiedFiles}/${p.totalFiles}")
                                }
                            }
                            ui.installedPath != null -> {
                                Text(
                                    text = "Instalado en:\n${ui.installedPath}",
                                    textAlign = TextAlign.Center
                                )
                            }
                            ui.installError != null -> {
                                Text("Error: ${ui.installError}", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider()

                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Prompt") }
                        )

                        Button(
                            onClick = { vm.generateWithMLC(app, prompt) },
                            enabled = ui.installedPath != null && !ui.isGenerating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (ui.isGenerating) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Generando…")
                            } else {
                                Text("Generar con MLC")
                            }
                        }

                        if (ui.output.isNotBlank()) {
                            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                                Text(ui.output, Modifier.padding(16.dp))
                            }
                            ui.lastGenMs?.let {
                                Text("⏱ ${it} ms", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        ui.genError?.let {
                            Text("Error: $it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
