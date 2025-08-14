package com.redornitier.cookia

import ai.mlc.mlcllm.MLCEngine
import android.app.Application

class App : Application() {
    lateinit var engine: MLCEngine
        private set

    override fun onCreate() {
        super.onCreate()
        // Tu build de mlc4j expone MLCEngine() sin argumentos
        engine = MLCEngine()

        // Si tu versión tiene init(context), puedes (opcional):
        // engine.init(this)
    }
}