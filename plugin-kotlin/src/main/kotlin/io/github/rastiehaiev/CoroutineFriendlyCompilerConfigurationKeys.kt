package io.github.rastiehaiev

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

private val KEY_ENABLED = CompilerConfigurationKey.create<Boolean>("enabled")

class TemplateCompilerConfigurationKeys(
    private val config: CompilerConfiguration,
) {

    val enabled: Boolean
        get() = config.get(KEY_ENABLED) == true

    fun setEnabled(value: String) {
        config.put(KEY_ENABLED, value.toBoolean())
    }
}

fun CompilerConfiguration.toKeys(): TemplateCompilerConfigurationKeys = TemplateCompilerConfigurationKeys(this)
