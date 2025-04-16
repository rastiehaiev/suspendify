package io.github.rastiehaiev.model

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class Meta {
    object OriginalClass {
        val factoryMethodName = Name.identifier("suspendify")
    }

    object SuspendifiedClass {
        val name = Name.identifier("Suspendified")

        val delegateParameterName = Name.identifier("delegate")
        val dispatcherParameter = Parameter(
            name = Name.identifier("dispatcher"),
            type = ClassId(
                packageFqName = FqName("kotlinx.coroutines"),
                topLevelName = Name.identifier("CoroutineDispatcher"),
            )
        )
    }

    class Parameter(val name: Name, val type: ClassId)
}
