package io.github.rastiehaiev.fir

import io.github.rastiehaiev.model.DeclarationKey
import io.github.rastiehaiev.model.Function
import io.github.rastiehaiev.model.Meta
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
        +::SuspendifyDeclarationGenerationExtension
    }
}

private class SuspendifyDeclarationGenerationExtension(
    session: FirSession,
) : FirDeclarationGenerationExtension(session) {
    private val markAnnotationFqdn: FqName =
        ClassId(
            packageFqName = FqName("io.github.rastiehaiev"),
            topLevelName = Name.identifier("Suspendify"),
        ).asSingleFqName()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(DeclarationPredicate.create { annotated(markAnnotationFqdn) })
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> =
        when (classSymbol.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.OriginalClass -> setOf(Meta.SuspendifiedClass.name)
            else -> emptySet()
        }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        val declarationKey = owner.getDeclarationKey<DeclarationKey.OriginalClass>()
        return if (declarationKey != null && name == Meta.SuspendifiedClass.name) {
            createNestedClassStub(owner, name).symbol
        } else {
            super.generateNestedClassLikeDeclaration(owner, name, context)
        }
    }

    @OptIn(SymbolInternals::class)
    private fun createNestedClassStub(owner: FirClassSymbol<*>, name: Name): FirRegularClass {
        val functions = owner.fir.declarations
            .filterIsInstance<FirSimpleFunction>()
            .map { function -> function.toMetaFunction() }
            .associateBy { it.name }

        val key = DeclarationKey.SuspendifiedClass(owner, functions)
        return createNestedClass(owner, name, key) {
            visibility = Visibilities.Public
            modality = Modality.FINAL
        }
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext) =
        when (val declarationKey = classSymbol.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.OriginalClass -> setOf(Meta.OriginalClass.factoryMethodName)
            is DeclarationKey.SuspendifiedClass -> {
                declarationKey.functions.keys + listOf(
                    SpecialNames.INIT,
                    Meta.SuspendifiedClass.delegateParameterName,
                    Meta.SuspendifiedClass.dispatcherParameter.name,
                )
            }

            else -> emptySet()
        }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner
        return when (val declarationKey = owner?.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.SuspendifiedClass -> declarationKey.createSuspendedFunction(callableId, owner)
            is DeclarationKey.OriginalClass -> context.createSuspendifyFunctionInOriginalClass(callableId, owner)
            else -> emptyList()
        }
    }

    private fun DeclarationKey.SuspendifiedClass.createSuspendedFunction(
        callableId: CallableId,
        suspendifiedClass: FirClassSymbol<*>,
    ): List<FirNamedFunctionSymbol> {
        val function = functions[callableId.callableName] ?: return emptyList()
        val key = DeclarationKey.SuspendifiedClassFunction(
            originalClassId = originalClass.classId,
            suspendifiedClassId = suspendifiedClass.classId,
        )
        val suspendedFunction = createMemberFunction(
            owner = suspendifiedClass,
            key = key,
            name = callableId.callableName,
            returnType = function.returnType,
        ) {
            function.parameters.forEach { parameter ->
                with(parameter) { valueParameter(name, type) }
            }
            status { isSuspend = true }
        }
        return listOf(suspendedFunction.symbol)
    }

    private fun MemberGenerationContext.createSuspendifyFunctionInOriginalClass(
        callableId: CallableId,
        originalClass: FirClassSymbol<*>,
    ): List<FirNamedFunctionSymbol> {
        val suspendifiedClass = findClassSymbols(Meta.SuspendifiedClass.name)
            .takeIf { it.size == 1 }
            ?.first()
            ?: return emptyList()


        val suspendifyFunction = createMemberFunction(
            owner = originalClass,
            key = DeclarationKey.OriginalClassConvertMethod(suspendifiedClass.classId),
            name = callableId.callableName,
            returnType = suspendifiedClass.defaultType(),
        ) {
            with(Meta.SuspendifiedClass) {
                valueParameter(
                    name = dispatcherParameter.name,
                    type = dispatcherParameter.type.findFirClassSymbol().defaultType(),
                    // should be Dispatchers.IO
                    hasDefaultValue = false,
                )
            }
        }
        return listOf(suspendifyFunction.symbol)
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
                with(Meta.SuspendifiedClass) {
                    valueParameter(
                        name = delegateParameterName,
                        type = declarationKey.originalClass.defaultType(),
                    )
                    valueParameter(
                        name = dispatcherParameter.name,
                        type = dispatcherParameter.type.findFirClassSymbol().defaultType(),
                    )
                }
            }
            listOf(constructor.symbol)
        } else {
            super.generateConstructors(context)
        }
    }

    private inline fun <reified K : DeclarationKey> FirClassSymbol<*>.getDeclarationKey(): K? =
        if (isSuspendifyAnnotated() && isClass) {
            DeclarationKey.OriginalClass
        } else {
            (origin as? FirDeclarationOrigin.Plugin)?.key
        }.let {
            it as? K
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

    private fun FirSimpleFunction.toMetaFunction(): Function =
        Function(
            name = name,
            returnType = returnTypeRef.coneType,
            parameters = valueParameters.map { parameter ->
                Parameter(
                    name = parameter.name,
                    type = parameter.returnTypeRef.coneType,
                )
            }
        )
}
