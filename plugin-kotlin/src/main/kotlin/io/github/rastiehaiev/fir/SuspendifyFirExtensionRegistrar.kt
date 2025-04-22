package io.github.rastiehaiev.fir

import io.github.rastiehaiev.error
import io.github.rastiehaiev.getLogger
import io.github.rastiehaiev.info
import io.github.rastiehaiev.model.DeclarationKey
import io.github.rastiehaiev.model.Function
import io.github.rastiehaiev.model.Meta
import io.github.rastiehaiev.model.Parameter
import io.github.rastiehaiev.warn
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
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
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class SuspendifyFirExtensionRegistrar(configuration: CompilerConfiguration) : FirExtensionRegistrar() {
    private val logger = configuration.getLogger()

    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ it: FirSession -> SuspendifyDeclarationGenerationExtension(it, logger) }
    }
}

private class SuspendifyDeclarationGenerationExtension(
    session: FirSession,
    private val logger: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
    private val markAnnotationFqdn: FqName = Meta.ClassIds.Suspendify.asSingleFqName()

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
            createNestedClassStub(owner, name)?.symbol
        } else {
            super.generateNestedClassLikeDeclaration(owner, name, context)
        }
    }

    @OptIn(SymbolInternals::class)
    private fun createNestedClassStub(owner: FirClassSymbol<*>, name: Name): FirRegularClass? {
        val functions = owner.fir.declarations
            .filterIsInstance<FirSimpleFunction>()
            .mapNotNull { function -> function.toMetaFunction(owner.classId) }
            .associateBy { it.name }

        return if (functions.isNotEmpty()) {
            val key = DeclarationKey.SuspendifiedClass(owner, functions)
            logger.info("Creating nested class '$name' for '${owner.classId.asString()}'.")
            createNestedClass(owner, name, key) {
                visibility = Visibilities.Public
                modality = Modality.FINAL
            }
        } else {
            logger.warn("Class '${owner.name}' has no methods to suspendify. '${Meta.SuspendifiedClass.name}' nested class won't be created.")
            null
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
        logger.info("Creating suspended function '${callableId.callableName}' in '${suspendifiedClass.classId.asString()}'.")
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
            with(Meta.OriginalClass) {
                factoryMethodParameters.forEach { parameter ->
                    valueParameter(
                        name = parameter.name,
                        type = parameter.type.findFirClassSymbol().defaultType(),
                        hasDefaultValue = parameter.hasDefaultValue,
                    )
                }
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
        if (isAnnotatedWith(markAnnotationFqdn)) {
            if (isClass) {
                DeclarationKey.OriginalClass
            } else {
                logger.error("Only classes can be annotated with '${markAnnotationFqdn}'!")
                null
            }
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
            ?: error("Could not find class by ID='$this'.")

    private fun FirClassSymbol<*>.isAnnotatedWith(fqName: FqName): Boolean =
        annotations.any { annotation -> annotation.fqName(session) == fqName }

    private fun FirSimpleFunction.toMetaFunction(classId: ClassId): Function? {
        val functionReturnType = returnTypeRef.coneTypeOrNull
        if (functionReturnType == null) {
            logger.warn(
                "The function '$name' of class `${classId.asString()}` won't be created " +
                    "as unable to determine its return type. " +
                    "Please, consider specifying the return type explicitly.)"
            )
        }

        return functionReturnType?.let {
            Function(
                name = name,
                returnType = functionReturnType,
                parameters = valueParameters.map { parameter ->
                    Parameter(
                        name = parameter.name,
                        type = parameter.returnTypeRef.coneType,
                    )
                }
            )
        }
    }
}
