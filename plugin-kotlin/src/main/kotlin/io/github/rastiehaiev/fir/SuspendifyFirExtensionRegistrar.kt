package io.github.rastiehaiev.fir

import io.github.rastiehaiev.error
import io.github.rastiehaiev.getLogger
import io.github.rastiehaiev.info
import io.github.rastiehaiev.model.DeclarationKey
import io.github.rastiehaiev.model.FunctionSpec
import io.github.rastiehaiev.model.Meta
import io.github.rastiehaiev.model.ParameterSpec
import io.github.rastiehaiev.warn
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.utils.isClass
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
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
    private val markAnnotationFqdn: FqName = Meta.ClassIds.Suspendifyable.asSingleFqName()

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

    private fun createNestedClassStub(owner: FirClassSymbol<*>, name: Name): FirRegularClass? {
        val key = DeclarationKey.SuspendifiedClass(owner)
        logger.info("Creating nested class '$name' for '${owner.classId.asString()}'.")
        return createNestedClass(owner, name, key) {
            visibility = Visibilities.Public
            modality = Modality.FINAL
        }
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext) =
        when (val key = classSymbol.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.OriginalClass -> setOf(Meta.OriginalClass.factoryMethodName)
            is DeclarationKey.SuspendifiedClass -> {
                val functionsNames = key.originalClass.getFunctions().map { it.name }.toSet()
                functionsNames + SpecialNames.INIT
            }

            else -> emptySet()
        }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner
        return when (val declarationKey = owner?.getDeclarationKey<DeclarationKey>()) {
            is DeclarationKey.SuspendifiedClass -> declarationKey.createSuspendFunctions(callableId, owner)
            is DeclarationKey.OriginalClass -> context.createSuspendifyFunctionInOriginalClass(callableId, owner)
            else -> emptyList()
        }
    }

    @OptIn(SymbolInternals::class)
    private fun DeclarationKey.SuspendifiedClass.createSuspendFunctions(
        callableId: CallableId,
        suspendifiedClass: FirClassSymbol<*>,
    ): List<FirNamedFunctionSymbol> {
        val originalClassId = originalClass.classId
        val key = DeclarationKey.SuspendifiedClassFunction(
            originalClassId = originalClassId,
            suspendifiedClassId = suspendifiedClass.classId,
        )
        return originalClass.getFunctions()
            .filter { it.name == callableId.callableName }
            .onEach {
                if (it.isSuspend) logger.warn("Function '${it.name}' of class '$originalClassId' is already suspend.")
            }
            .filter { !it.isSuspend }
            .mapNotNull { it.toFunctionSpec(originalClassId) }
            .map { functionSpec ->
                logger.warn("Creating suspend function '${functionSpec.name}' in '${suspendifiedClass.classId}'.")
                createMemberFunction(
                    owner = suspendifiedClass,
                    key = key,
                    name = functionSpec.name,
                    returnType = functionSpec.returnType,
                ) {
                    status { isSuspend = true }
                    functionSpec.parameters.forEach { parameter ->
                        valueParameter(parameter.name, parameter.type)
                    }
                }
            }
            .map { it.symbol }
            .toList()
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

    private fun FirSimpleFunction.toFunctionSpec(classId: ClassId): FunctionSpec? {
        val functionName = name.asString()
        fun FirValueParameter.toParameter(): ParameterSpec? {
            val parameterType = returnTypeRef.coneTypeOrNull
            val parameterName = this.name
            if (parameterType == null) {
                logger.warn(
                    "Unable to resolve type for value parameter '$parameterName' " +
                        "(function: '$functionName', class: '${classId.asString()}')."
                )
            }
            return parameterType?.let { ParameterSpec(name = parameterName, type = it) }
        }

        val functionReturnType = returnTypeRef.coneTypeOrNull
        if (functionReturnType == null) {
            logger.warn(
                "The function '$functionName' of class `${classId.asString()}` won't be created " +
                    "as unable to determine its return type (FIR type ref: '${returnTypeRef::class.simpleName}'). " +
                    "Please, consider specifying the return type explicitly.)"
            )
        }

        return functionReturnType?.let {
            val valueParameters = this.valueParameters
            val parameters = valueParameters.mapNotNull { it.toParameter() }
            if (parameters.size != valueParameters.size) {
                logger.warn(
                    "The function '$functionName' of class `${classId.asString()}` won't be created " +
                        "as unable to determine the return type of some of its value parameters.)"
                )
                null
            } else {
                FunctionSpec(name = name, returnType = functionReturnType, parameters = parameters)
            }
        }
    }

    @OptIn(SymbolInternals::class)
    private fun FirClassSymbol<*>.getFunctions(): List<FirSimpleFunction> {
        return fir.declarations.filterIsInstance<FirSimpleFunction>()
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
}
