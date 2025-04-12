package io.github.rastiehaiev

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class CoroutineFriendlyCommandLineProcessor : CommandLineProcessor {
    override val pluginId = with(PluginConfiguration) { "$GROUP_ID.$ARTIFACT_ID_GRADLE" }

    override val pluginOptions = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Specifies whether the '${PluginConfiguration.ARTIFACT_ID_GRADLE}' plugin is enabled.",
            required = true,
            allowMultipleOccurrences = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        val configurationKeys = configuration.toKeys()
        when (option.optionName) {
            "enabled" -> configurationKeys.setEnabled(value)
            else -> error("Unexpected config option: ${option.optionName}.")
        }
    }
}
