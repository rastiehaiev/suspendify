package io.github.rastiehaiev

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

fun CompilerConfiguration.getLogger(): MessageCollector {
    return get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
}

fun MessageCollector.warn(message: String) {
    report(CompilerMessageSeverity.WARNING, message)
}

fun MessageCollector.error(message: String) {
    report(CompilerMessageSeverity.ERROR, message)
}

fun MessageCollector.info(message: String) {
    report(CompilerMessageSeverity.INFO, message)
}
