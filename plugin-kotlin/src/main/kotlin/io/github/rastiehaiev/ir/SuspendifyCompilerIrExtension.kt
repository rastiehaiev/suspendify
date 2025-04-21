package io.github.rastiehaiev.ir

import io.github.rastiehaiev.getLogger
import io.github.rastiehaiev.model.DeclarationKey
import io.github.rastiehaiev.model.Meta
import io.github.rastiehaiev.warn
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addDefaultGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class SuspendifyCompilerIrExtension(private val configuration: CompilerConfiguration) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val logger = configuration.getLogger()
        moduleFragment.transformChildrenVoid(SuspendifyTransformer(pluginContext, logger))
    }
}

private class SuspendifyTransformer(
    private val pluginContext: IrPluginContext,
    private val logger: MessageCollector,
) : IrElementTransformerVoid() {

    override fun visitConstructor(declaration: IrConstructor): IrStatement {
        val pluginKey = declaration.origin.getPluginKey<DeclarationKey.SuspendifiedClassConstructor>()
            ?: return super.visitConstructor(declaration)

        declaration.body = irBuilder(declaration.symbol).irBlockBody {
            +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
            +IrInstanceInitializerCallImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                classSymbol = pluginKey.suspendifiedClassId.toClassSymbol(),
                type = context.irBuiltIns.unitType,
            )
        }

        val valueParameters = with(Meta.SuspendifiedClass) {
            listOf(
                dispatcherParameter.name to dispatcherParameter.type.toIrType(),
                delegateParameterName to pluginKey.originalClassId.toIrType()
            )
        }

        val nestedStubClass = declaration.parentAsClass
        val propertiesData = valueParameters.map { (valueParamName, valueParamType) ->
            val valueParameter = declaration.valueParameters.first { it.name == valueParamName }
            Triple(valueParamName, valueParamType, valueParameter)
        }

        propertiesData.forEach { (propertyName, propertyType, valueParameter) ->
            val property = nestedStubClass.addProperty {
                name = propertyName
                visibility = DescriptorVisibilities.PRIVATE
            }

            property.addBackingField {
                type = propertyType
            }.apply {
                initializer = factory.createExpressionBody(
                    IrGetValueImpl(
                        startOffset = UNDEFINED_OFFSET,
                        endOffset = UNDEFINED_OFFSET,
                        type = valueParameter.type,
                        symbol = valueParameter.symbol,
                        origin = IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER,
                    )
                )
            }

            property.addDefaultGetter(nestedStubClass, pluginContext.irBuiltIns) {
                visibility = DescriptorVisibilities.PRIVATE
            }
        }
        return super.visitConstructor(declaration)
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        when (val pluginKey = declaration.origin.getPluginKey<DeclarationKey>()) {
            is DeclarationKey.SuspendifiedClassFunction -> {
                declaration.body = pluginKey.createSuspendFunctionBlockBody(declaration)
            }

            is DeclarationKey.OriginalClassConvertMethod -> {
                declaration.body = pluginKey.createSuspendifyInstanceBlockBody(declaration)
            }

            else -> {}
        }
        return super.visitFunction(declaration)
    }

    private fun DeclarationKey.OriginalClassConvertMethod.createSuspendifyInstanceBlockBody(
        declaration: IrFunction,
    ): IrBlockBody {
        val constructorSymbol = suspendifiedClassId.toClassSymbol()
            .constructors
            .firstOrNull()
            ?: warnWithError("Expected constructor in '$suspendifiedClassId'.")

        val delegateParam = declaration.dispatchReceiverParameter ?: error("Expected 'delegate' param.")
        val dispatcherParam = declaration.valueParameters.firstOrNull() ?: error("Expected 'dispatcher' param.")

        return irBuilder(declaration.symbol).irBlockBody {
            +irReturn(
                irCall(
                    callee = constructorSymbol,
                    type = declaration.returnType,
                ).apply {
                    putValueArgument(0, irGet(delegateParam))
                    putValueArgument(1, irGet(dispatcherParam))
                }
            )
        }
    }

    private fun DeclarationIrBuilder.dispatchersIoAsExpression(): IrExpressionBody {
        val dispatchersClassSymbol: IrClassSymbol = Meta.ClassIds.Dispatchers.toClassSymbol()
        val ioGetter: IrSimpleFunctionSymbol = dispatchersClassSymbol.getPropertyGetterOrError("IO")

        val dispatcherIoExpression = irGet(
            type = Meta.ClassIds.CoroutineDispatcher.toIrType(),
            getterSymbol = ioGetter,
            receiver = irGetObject(dispatchersClassSymbol)
        )
        return irExprBody(dispatcherIoExpression)
    }

    private fun DeclarationKey.SuspendifiedClassFunction.createSuspendFunctionBlockBody(
        declaration: IrFunction,
    ): IrBlockBody {
        val (delegateGetterSymbol, dispatcherGetterSymbol) = with(Meta.SuspendifiedClass) {
            with(suspendifiedClassId.toClassSymbol()) {
                Pair(
                    getPropertyGetterOrError(delegateParameterName.asString()),
                    getPropertyGetterOrError(dispatcherParameter.name.asString())
                )
            }
        }

        val thisReceiver = declaration.dispatchReceiverParameter ?: warnWithError("Expected dispatch receiver param.")
        val declarationClass = declaration.parent as IrClass

        val originalClassSymbol = originalClassId.toClassSymbol()
        val coroutineScopeType = Meta.ClassIds.CoroutineScope.toIrType()
        val originalFunction = originalClassSymbol.findMatchingFunction(declaration)
            ?: warnWithError("Expected a function '${declaration.name}' in original class '${originalClassSymbol.owner.name}'.")

        val functionLambda = declaration.factory.buildFun {
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            modality = Modality.FINAL
            returnType = declaration.returnType
            isSuspend = true
        }.apply {
            parent = declaration
            body = irBuilder(symbol).irBlockBody {
                +irReturn(
                    irCall(
                        callee = originalFunction.symbol,
                        type = declaration.returnType,
                    ).apply {
                        dispatchReceiver = irCall(delegateGetterSymbol).apply {
                            origin = IrStatementOrigin.GET_PROPERTY
                            dispatchReceiver = irGet(thisReceiver, type = declarationClass.defaultType)
                        }

                        declaration.valueParameters.forEachIndexed { index, param ->
                            putValueArgument(index, irGet(param))
                        }
                    }
                )
            }
            addExtensionReceiver(coroutineScopeType, name = Name.identifier("\$this\$withContext"))
        }

        val lambda = IrFunctionExpressionImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            // SuspendFunction1<CoroutineScope, T>
            type = suspendFunction1Type(parameterizedWith = listOf(coroutineScopeType, declaration.returnType)),
            origin = IrStatementOrigin.LAMBDA,
            function = functionLambda,
        )

        val coroutineDispatcherType = Meta.ClassIds.CoroutineDispatcher.toIrType()
        val withContextFunction = pluginContext.referenceFunctions(Meta.CallableIds.withContext)
            .firstOrNull { it.owner.valueParameters.size == 2 }
            ?: warnWithError("Expected function '${Meta.CallableIds.withContext}'.")

        return irBuilder(declaration.symbol).irBlockBody {
            val call = irCall(
                callee = withContextFunction,
                type = declaration.returnType,
            ).apply {
                putTypeArgument(0, declaration.returnType)

                val dispatcherArgument = irCall(dispatcherGetterSymbol, type = coroutineDispatcherType).apply {
                    origin = IrStatementOrigin.GET_PROPERTY
                    dispatchReceiver = irGet(thisReceiver, type = declarationClass.defaultType)
                }
                putValueArgument(0, dispatcherArgument)
                putValueArgument(1, lambda)
            }
            if (declaration.returnType == context.irBuiltIns.unitType) {
                +call
            } else {
                +irReturn(call)
            }
        }
    }

    private fun IrProperty.addDefaultGetter(
        parentClass: IrClass,
        builtIns: IrBuiltIns,
        configure: IrSimpleFunction.() -> Unit,
    ) {
        addDefaultGetter(parentClass, builtIns)

        getterOrFail.apply {
            dispatchReceiverParameter!!.origin = IrDeclarationOrigin.DEFINED
            configure()
        }
    }

    override fun visitValueParameter(declaration: IrValueParameter): IrStatement {
        val pluginKey = declaration.origin.getPluginKey<DeclarationKey.OriginalClassConvertMethod>()
        if (pluginKey != null) {
            declaration.defaultValue = irBuilder(declaration.symbol).dispatchersIoAsExpression()
            return declaration
        }
        return super.visitValueParameter(declaration)
    }

    private inline fun <reified K : DeclarationKey> IrDeclarationOrigin.getPluginKey(): K? {
        val generatedByPlugin = this as? IrDeclarationOrigin.GeneratedByPlugin ?: return null
        return generatedByPlugin.pluginKey as? K
    }

    private fun ClassId.toClassSymbol(): IrClassSymbol =
        pluginContext.referenceClass(this) ?: warnWithError("Class '$this' not found")

    private fun ClassId.toIrType(): IrType = toClassSymbol().defaultType

    private fun irBuilder(symbol: IrSymbol): DeclarationIrBuilder =
        DeclarationIrBuilder(pluginContext, symbol, symbol.owner.startOffset, symbol.owner.endOffset)

    private fun IrSimpleFunction.addExtensionReceiver(type: IrType, name: Name): IrValueParameter =
        IrValueParameterBuilder().run {
            this.type = type
            this.origin = IrDeclarationOrigin.DEFINED
            this.name = name
            factory.buildValueParameter(this, this@addExtensionReceiver).also { receiver ->
                extensionReceiverParameter = receiver
            }
        }

    private val IrProperty.getterOrFail: IrSimpleFunction
        get() = getter ?: warnWithError("'getter' should be present, but was null:\n${dump()}")

    private fun IrClassSymbol.getPropertyGetterOrError(name: String): IrSimpleFunctionSymbol =
        this.getPropertyGetter(name) ?: warnWithError("Getter for property '$name' not found.")

    private fun suspendFunction1Type(parameterizedWith: List<IrType>): IrType {
        val extensionAnnotationSymbol = Meta.ClassIds.ExtensionFunctionType.toClassSymbol()
        val extensionAnnotationConstructor = extensionAnnotationSymbol.constructors.first()

        val extensionAnnotation = IrConstructorCallImpl.fromSymbolOwner(
            startOffset = 0,
            endOffset = 0,
            type = extensionAnnotationSymbol.defaultType,
            constructorSymbol = extensionAnnotationConstructor,
        )
        return Meta.ClassIds.SuspendFunction1
            .toClassSymbol()
            .typeWith(parameterizedWith)
            .addAnnotations(newAnnotations = listOf(extensionAnnotation))
    }

    private fun IrClassSymbol.findMatchingFunction(declaration: IrFunction): IrSimpleFunction? =
        owner.functions
            .filter { it.name == declaration.name }
            .firstOrNull { func ->
                val originalParameters = func.valueParameters
                val generatedParameters = declaration.valueParameters
                originalParameters.map { it.name to it.type } == generatedParameters.map { it.name to it.type }
            }

    private fun warnWithError(message: String): Nothing {
        logger.warn(message)
        error(message)
    }
}
