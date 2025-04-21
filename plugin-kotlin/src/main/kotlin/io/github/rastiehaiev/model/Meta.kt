package io.github.rastiehaiev.model

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object Meta {
    object OriginalClass {
        val factoryMethodName = Name.identifier("suspendify")
        val factoryMethodParameters = listOf(
            Parameter(
                name = Name.identifier("with"),
                type = ClassIds.CoroutineDispatcher,
            ),
        )
    }

    object SuspendifiedClass {
        val name = Name.identifier("Suspendified")
        val delegateParameterName = Name.identifier("delegate")
        val dispatcherParameter = Parameter(
            name = Name.identifier("dispatcher"),
            type = ClassIds.CoroutineDispatcher,
        )
    }

    object ClassIds {
        val SuspendFunction1 = ClassId(
            packageFqName = FqName("kotlin.coroutines"),
            topLevelName = Name.identifier("SuspendFunction1"),
        )
        val CoroutineScope = ClassId(
            packageFqName = FqName("kotlinx.coroutines"),
            topLevelName = Name.identifier("CoroutineScope"),
        )
        val CoroutineDispatcher = ClassId(
            packageFqName = FqName("kotlinx.coroutines"),
            topLevelName = Name.identifier("CoroutineDispatcher"),
        )
        val Dispatchers = ClassId(
            packageFqName = FqName("kotlinx.coroutines"),
            topLevelName = Name.identifier("Dispatchers"),
        )
        val ExtensionFunctionType = ClassId(
            packageFqName = FqName("kotlin"),
            topLevelName = Name.identifier("ExtensionFunctionType")
        )
        val Suspendify = ClassId(
            packageFqName = FqName("io.github.rastiehaiev"),
            topLevelName = Name.identifier("Suspendify"),
        )
    }

    object CallableIds {
        val withContext = CallableId(
            packageName = FqName("kotlinx.coroutines"),
            callableName = Name.identifier("withContext"),
        )
    }

    class Parameter(val name: Name, val type: ClassId, val hasDefaultValue: Boolean = false)
}
