package io.github.rastiehaiev

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.name.Name

sealed class CoroutineFriendlyKey : GeneratedDeclarationKey() {
    data object OriginalClass : CoroutineFriendlyKey()

    class NestedStubClass(
        val originalClass: FirClassSymbol<*>,
        val functions: Map<Name, FirSimpleFunction>,
    ) : CoroutineFriendlyKey()

    class NestedStubClassConstructor(
        val originalClass: FirClassSymbol<*>,
        val nestedStubClass: FirClassSymbol<*>,
    ) : CoroutineFriendlyKey()

    class NestedStubClassProperty(
        val originalClass: FirClassSymbol<*>,
        val nestedStubClass: FirClassSymbol<*>,
    ) : CoroutineFriendlyKey()

    class NestedStubClassFunction(
        val originalClass: FirClassSymbol<*>,
        val nestedStubClass: FirClassSymbol<*>,
        val nestedStubClassFunction: FirSimpleFunction,
    ) : CoroutineFriendlyKey()

    class OriginalClassConvertMethod(
        val nestedStubClass: FirClassSymbol<*>,
    ) : CoroutineFriendlyKey()
}
