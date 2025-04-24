package io.github.rastiehaiev.model

import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.Name

data class FunctionSpec(
    val name: Name,
    val returnType: ConeKotlinType,
    val parameters: List<ParameterSpec>,
)

data class ParameterSpec(
    val name: Name,
    val type: ConeKotlinType,
)
