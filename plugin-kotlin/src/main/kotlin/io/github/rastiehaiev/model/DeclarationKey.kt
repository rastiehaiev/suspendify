package io.github.rastiehaiev.model

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

sealed class DeclarationKey : GeneratedDeclarationKey() {
    data object OriginalClass : DeclarationKey()

    class OriginalClassConvertMethod(
        val suspendifiedClassId: ClassId,
    ) : DeclarationKey()

    class SuspendifiedClass(
        val originalClass: FirClassSymbol<*>,
        val functions: Map<Name, List<Function>>,
    ) : DeclarationKey()

    class SuspendifiedClassConstructor(
        val originalClassId: ClassId,
        val suspendifiedClassId: ClassId,
    ) : DeclarationKey()

    class SuspendifiedClassFunction(
        val originalClassId: ClassId,
        val suspendifiedClassId: ClassId,
    ) : DeclarationKey()
}
