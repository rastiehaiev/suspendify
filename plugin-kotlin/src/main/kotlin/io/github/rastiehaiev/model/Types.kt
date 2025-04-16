package io.github.rastiehaiev.model

import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.Name

data class Function(
    val name: Name,
    val returnType: ConeKotlinType,
    val parameters: List<Parameter>,
)

data class Parameter(
    val name: Name,
    val type: ConeKotlinType,
)
