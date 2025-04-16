package io.github.rastiehaiev

import io.github.rastiehaiev.fir.SuspendifyFirExtensionRegistrar
import io.github.rastiehaiev.ir.SuspendifyCompilerIrExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@Suppress("Unused")
@OptIn(ExperimentalCompilerApi::class)
class SuspendifyCompilerPlugin : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val configurationKeys = configuration.toKeys()
        if (configurationKeys.enabled) {
            FirExtensionRegistrarAdapter.registerExtension(SuspendifyFirExtensionRegistrar())
            IrGenerationExtension.registerExtension(SuspendifyCompilerIrExtension())
        }
    }
}
