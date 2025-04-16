package io.github.rastiehaiev.ir

import io.github.rastiehaiev.SuspendifyKey
import io.github.rastiehaiev.log
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
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
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
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
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
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
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class SuspendifyCompilerIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(SuspendifyTransformer(pluginContext))
    }
}

private class SuspendifyTransformer(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {
    private val suspendFunction1ClassId = ClassId(
        packageFqName = FqName("kotlin.coroutines"),
        topLevelName = Name.identifier("SuspendFunction1"),
    )
    private val coroutineDispatcherClassId = ClassId(
        packageFqName = FqName("kotlinx.coroutines"),
        topLevelName = Name.identifier("CoroutineDispatcher"),
    )
    private val coroutineScopeClassId = ClassId(
        packageFqName = FqName("kotlinx.coroutines"),
        topLevelName = Name.identifier("CoroutineScope"),
    )
    private val withContextClassId = CallableId(
        packageName = FqName("kotlinx.coroutines"),
        callableName = Name.identifier("withContext"),
    )

    override fun visitConstructor(declaration: IrConstructor): IrStatement {
        val pluginKey = declaration.origin.getPluginKey<SuspendifyKey.NestedStubClassConstructor>()
            ?: return super.visitConstructor(declaration)

        declaration.body = irBuilder(declaration.symbol).irBlockBody {
            +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
            +IrInstanceInitializerCallImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                classSymbol = pluginContext.findClassSymbol(pluginKey.nestedStubClass.classId),
                type = context.irBuiltIns.unitType,
            )
        }

        val nestedStubClass = declaration.parentAsClass

        val coroutineDispatcherType = pluginContext.findClassSymbol(coroutineDispatcherClassId).defaultType
        val delegateType = pluginContext.findClassSymbol(pluginKey.originalClass.classId).defaultType

        val valueParameters = listOf(
            "delegate" to delegateType,
            "dispatcher" to coroutineDispatcherType,
        )

        val propertiesData = valueParameters.map { (valueParamName, valueParamType) ->
            val valueParameter = declaration.valueParameters.first { it.name == Name.identifier(valueParamName) }
            Triple(valueParamName, valueParamType, valueParameter)
        }

        propertiesData.forEach { (propertyName, propertyType, valueParameter) ->
            val property = nestedStubClass.addProperty {
                name = Name.identifier(propertyName)
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

    private val IrProperty.getterOrFail: IrSimpleFunction
        get() {
            return getter ?: error("'getter' should be present, but was null: ${dump()}")
        }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        when (val pluginKey = declaration.origin.getPluginKey<SuspendifyKey>()) {
            is SuspendifyKey.NestedStubClassFunction -> {
                declaration.body = try {
                    pluginKey.createSuspendFunctionBlockBody(declaration)
                } catch (t: Throwable) {
                    null
                } ?: callTodoFunction(declaration)

                log("Parent:\n${declaration.parent.dump()}")
            }

            is SuspendifyKey.OriginalClassConvertMethod -> {
                declaration.body = pluginKey.createSuspendifyInstanceBlockBody(declaration)
            }

            else -> {}
        }
        return super.visitFunction(declaration)
    }

    private fun SuspendifyKey.OriginalClassConvertMethod.createSuspendifyInstanceBlockBody(
        declaration: IrFunction,
    ): IrBlockBody {
        val constructorSymbol = pluginContext.findClassSymbol(nestedStubClass.classId)
            .constructors
            .firstOrNull()
            ?: error("Expected constructor")

        val delegateParam = declaration.dispatchReceiverParameter ?: error("Expected delegate param")
        val dispatcherParam = declaration.valueParameters.firstOrNull() ?: error("Expected dispatcher param")

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

    private fun SuspendifyKey.NestedStubClassFunction.createSuspendFunctionBlockBody(
        declaration: IrFunction,
    ): IrBlockBody? {
        val nestedClassSymbol: IrClassSymbol = pluginContext.findClassSymbol(nestedStubClass.classId)
        val originalClassSymbol: IrClassSymbol = pluginContext.findClassSymbol(originalClass.classId)
        val declarationClass = declaration.parent as IrClass

        val withContextFunction: IrFunctionSymbol = pluginContext.referenceFunctions(withContextClassId)
            .firstOrNull { it.owner.valueParameters.size == 2 }
            ?: error("Expected function withContext")

        val delegateGetterSymbol: IrSimpleFunctionSymbol = nestedClassSymbol.getPropertyGetter("delegate")
            ?: error("Delegate not found")

        val dispatcherGetterSymbol = nestedClassSymbol.getPropertyGetter("dispatcher") ?: error("Dispatcher not found")

        val originalFunction: IrSimpleFunction = originalClassSymbol.owner.functions
            .filter { it.name == declaration.name }
            .firstOrNull { func ->
                val originalFuncParameters = func.valueParameters
                val generatedFuncParameters = declaration.valueParameters
                val orTypes = originalFuncParameters.map { it.name to it.type }
                val geTypes = generatedFuncParameters.map { it.name to it.type }
                originalFuncParameters.size == generatedFuncParameters.size && orTypes == geTypes
            } ?: run {
            return null
        }

        // SuspendFunction1<CoroutineScope, T>
        val coroutineScopeType = pluginContext.findClassSymbol(coroutineScopeClassId).defaultType
        val coroutineDispatcherType = pluginContext.findClassSymbol(coroutineDispatcherClassId).defaultType

        val a1 = pluginContext.findClassSymbol(
            ClassId(
                packageFqName = FqName("kotlin"),
                topLevelName = Name.identifier("ExtensionFunctionType")
            )
        )
        val a2 = a1.constructors.first()

        val extensionAnnotation = IrConstructorCallImpl.fromSymbolOwner(
            startOffset = 0,
            endOffset = 0,
            type = a1.defaultType,
            constructorSymbol = a2,
        )

        val lambdaType = pluginContext.findClassSymbol(suspendFunction1ClassId)
            .typeWith(coroutineScopeType, declaration.returnType)
            .addAnnotations(newAnnotations = listOf(extensionAnnotation))

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
                            dispatchReceiver =
                                irGet(declaration.dispatchReceiverParameter!!, type = declarationClass.defaultType)
                        }

                        declaration.valueParameters.forEachIndexed { index, param: IrValueParameter ->
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
            type = lambdaType,
            origin = IrStatementOrigin.LAMBDA,
            function = functionLambda,
        )

        val result = irBuilder(declaration.symbol).irBlockBody {
            val call = irCall(
                callee = withContextFunction,
                type = declaration.returnType,
            ).apply {
                putTypeArgument(0, declaration.returnType)

                val valueArgument = irCall(dispatcherGetterSymbol, type = coroutineDispatcherType).apply {
                    origin = IrStatementOrigin.GET_PROPERTY
                    dispatchReceiver =
                        irGet(declaration.dispatchReceiverParameter!!, type = declarationClass.defaultType)
                }
                putValueArgument(0, valueArgument)
                putValueArgument(1, lambda)
            }
            if (declaration.returnType == context.irBuiltIns.unitType) {
                +call
            } else {
                +irReturn(call)
            }
        }
        return result
    }

    private fun callTodoFunction(declaration: IrFunction): IrBlockBody {
        val errorFunction =
            pluginContext.referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("TODO")))
                .firstOrNull { it.owner.valueParameters.size == 1 }
                ?: error("Could not find kotlin.error(String)")

        return irBuilder(declaration.symbol).irBlockBody {
            +irReturn(
                irCall(
                    callee = errorFunction,
                ).apply {
                    putValueArgument(0, irString("The method is not implemented =("))
                }
            )
        }
    }

    private inline fun <reified K : SuspendifyKey> IrDeclarationOrigin.getPluginKey(): K? {
        val generatedByPlugin = this as? IrDeclarationOrigin.GeneratedByPlugin ?: return null
        return generatedByPlugin.pluginKey as K
    }

    private fun IrPluginContext.findClassSymbol(classId: ClassId): IrClassSymbol =
        referenceClass(classId) ?: error("Class '${classId}' not found")

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
}
