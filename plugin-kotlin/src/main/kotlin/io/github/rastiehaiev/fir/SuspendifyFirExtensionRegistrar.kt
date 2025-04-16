package io.github.rastiehaiev.fir

import io.github.rastiehaiev.model.DeclarationKey
import io.github.rastiehaiev.model.Function
import io.github.rastiehaiev.model.Parameter
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isClass
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.processClassifiersByName
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class SuspendifyFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyFirDeclarationGenerationExtension
    }
}

private class MyFirDeclarationGenerationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private val nestedClassName = Name.identifier("Suspendified")
    private val nestedClassDelegateValName = Name.identifier("delegate")
    private val nestedClassDispatcherValName = Name.identifier("dispatcher")

    private val markAnnotationFqdn: FqName =
        ClassId(
            packageFqName = FqName("io.github.rastiehaiev"),
            topLevelName = Name.identifier("Suspendify"),
        ).asSingleFqName()

    private val coroutineDispatcherClassId = ClassId(
        packageFqName = FqName("kotlinx.coroutines"),
        topLevelName = Name.identifier("CoroutineDispatcher"),
    )

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(DeclarationPredicate.create { annotated(markAnnotationFqdn) })
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        return when (classSymbol.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.OriginalClass -> setOf(nestedClassName)
            else -> emptySet()
        }
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        return if (name == nestedClassName) {
            createNestedClassStub(owner, name).symbol
        } else {
            super.generateNestedClassLikeDeclaration(owner, name, context)
        }
    }

    @OptIn(SymbolInternals::class)
    private fun createNestedClassStub(owner: FirClassSymbol<*>, name: Name): FirRegularClass {
        val functions = owner.fir.declarations
            .filterIsInstance<FirSimpleFunction>()
            .map { firSimpleFunction ->
                Function(
                    name = firSimpleFunction.name,
                    returnType = firSimpleFunction.returnTypeRef.coneType,
                    parameters = firSimpleFunction.valueParameters.map { firValueParameter ->
                        Parameter(firValueParameter.name, firValueParameter.returnTypeRef.coneType)
                    }
                )
            }
            .associateBy { it.name }

        val key = DeclarationKey.SuspendifiedClass(owner, functions)
        return createNestedClass(owner, name, key) {
            visibility = Visibilities.Public
            modality = Modality.FINAL
        }
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext) =
        when (val declarationKey = classSymbol.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.OriginalClass -> setOf(Name.identifier("suspendify"))

            is DeclarationKey.SuspendifiedClass -> {
                declarationKey.functions.map { Name.identifier(it.key.identifier) }.toSet() + listOf(
                    SpecialNames.INIT,
                    nestedClassDelegateValName,
                    nestedClassDispatcherValName,
                )
            }

            else -> super.getCallableNamesForClass(classSymbol, context)
        }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner
        return when (val declarationKey = owner?.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.SuspendifiedClass -> {
                val function = declarationKey.functions[callableId.callableName]
                if (function != null) {
                    val func =
                        createSuspendedStubClassFunction(declarationKey.originalClass, owner, function, callableId)
                    listOf(func.symbol)
                } else {
                    emptyList()
                }
            }

            is DeclarationKey.OriginalClass -> {
                val nestedStubClasses = context.findClassSymbols(nestedClassName)
                if (nestedStubClasses.size == 1) {
                    val nestedStubClass = nestedStubClasses.first()
                    listOf(createSuspendifyMemberFunction(owner, nestedStubClass, callableId).symbol)
                } else {
                    emptyList()
                }
            }

            else -> {
                super.generateFunctions(callableId, context)
            }
        }
    }

    private fun createSuspendedStubClassFunction(
        originalClass: FirClassSymbol<*>,
        suspendifiedClass: FirClassSymbol<*>,
        function: Function,
        callableId: CallableId,
    ): FirSimpleFunction {
        val key = DeclarationKey.SuspendifiedClassFunction(
            originalClass.classId,
            suspendifiedClass.classId,
        )

        val memberFunction = createMemberFunction(
            owner = suspendifiedClass,
            key = key,
            name = callableId.callableName,
            returnType = function.returnType,
        ) {

            function.parameters.forEach { parameter ->
                with(parameter) { valueParameter(name, type) }
            }
            status {
                isSuspend = true
            }
        }
        return memberFunction
    }

    private fun createSuspendifyMemberFunction(
        originalClass: FirClassSymbol<*>,
        nestedStubClass: FirClassSymbol<*>,
        callableId: CallableId,
    ): FirSimpleFunction {
        val returnType = nestedStubClass.defaultType()
        return createMemberFunction(
            owner = originalClass,
            key = DeclarationKey.OriginalClassConvertMethod(nestedStubClass),
            name = callableId.callableName,
            returnType = returnType,
        ) {
            valueParameter(
                name = Name.identifier("dispatcher"),
                type = coroutineDispatcherClassId.findFirClassSymbol().defaultType(),
                // should be Dispatchers.IO
                hasDefaultValue = false,
            )
        }
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val declarationKey = context.owner.getDeclarationKey<DeclarationKey.SuspendifiedClass>()
        return if (declarationKey != null) {
            val constructor = createConstructor(
                owner = context.owner,
                key = DeclarationKey.SuspendifiedClassConstructor(
                    originalClassId = declarationKey.originalClass.classId,
                    suspendifiedClassId = context.owner.classId,
                ),
                isPrimary = true,
                generateDelegatedNoArgConstructorCall = false,
            ) {
                visibility = Visibilities.Private
                valueParameter(
                    name = Name.identifier("delegate"),
                    type = declarationKey.originalClass.defaultType(),
                )
                valueParameter(
                    name = Name.identifier("dispatcher"),
                    type = coroutineDispatcherClassId.findFirClassSymbol().defaultType(),
                )
            }
            listOf(constructor.symbol)
        } else {
            super.generateConstructors(context)
        }
    }

    private inline fun <reified K : DeclarationKey> FirClassSymbol<*>.getDeclarationKey(): K? =
        if (isSuspendifyAnnotated() && isClass) {
            DeclarationKey.OriginalClass as? K
        } else {
            (origin as? FirDeclarationOrigin.Plugin)?.key as? K
        }

    private fun MemberGenerationContext.findClassSymbols(name: Name): List<FirClassSymbol<*>> =
        mutableSetOf<FirClassifierSymbol<*>>()
            .apply { declaredScope?.processClassifiersByName(name, ::add) }
            .mapNotNull { it as? FirClassSymbol<*> }

    private fun ClassId.findFirClassSymbol(): FirClassSymbol<*> =
        session.symbolProvider.getClassLikeSymbolByClassId(this)
            as? FirClassSymbol<*>
            ?: error("Could not find class by ID=$this.")

    private fun FirClassSymbol<*>.isSuspendifyAnnotated() =
        annotations.any { annotation -> annotation.fqName(session) == markAnnotationFqdn }
}
