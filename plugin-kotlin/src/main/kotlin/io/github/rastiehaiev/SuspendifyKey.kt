package io.github.rastiehaiev

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.name.Name

sealed class SuspendifyKey : GeneratedDeclarationKey() {
    data object OriginalClass : SuspendifyKey()

    class NestedStubClass(
        val originalClass: FirClassSymbol<*>,
        val functions: Map<Name, FirSimpleFunction>,
    ) : SuspendifyKey()

    class NestedStubClassConstructor(
        val originalClass: FirClassSymbol<*>,
        val nestedStubClass: FirClassSymbol<*>,
    ) : SuspendifyKey()

    class NestedStubClassProperty(
        val originalClass: FirClassSymbol<*>,
        val nestedStubClass: FirClassSymbol<*>,
    ) : SuspendifyKey()

    class NestedStubClassFunction(
        val originalClass: FirClassSymbol<*>,
        val nestedStubClass: FirClassSymbol<*>,
        val nestedStubClassFunction: FirSimpleFunction,
    ) : SuspendifyKey()

    class OriginalClassConvertMethod(
        val nestedStubClass: FirClassSymbol<*>,
    ) : SuspendifyKey()
}
