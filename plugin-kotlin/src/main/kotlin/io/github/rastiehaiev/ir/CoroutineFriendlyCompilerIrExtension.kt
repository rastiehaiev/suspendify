package io.github.rastiehaiev.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.konan.serialization.KonanManglerIr.signatureString
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.AbstractIrFileEntry
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File
import java.nio.file.Files

class CoroutineFriendlyCompilerIrExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val visitor = TemplateVisitor(moduleFragment, pluginContext)
        moduleFragment.acceptVoid(visitor)
    }
}

private class TemplateVisitor(
    private val moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext,
) : IrElementVisitorVoid {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        super.visitCall(expression)

        val asCoroutineFriendlyFuncCallableId = CallableId(
            packageName = FqName("io.github.rastiehaiev"),
            callableName = Name.identifier("asCoroutineFriendly"),
        )

        val asCoroutineFriendlyFuncSymbol =
            pluginContext.referenceFunctions(asCoroutineFriendlyFuncCallableId).firstOrNull()

        if (expression.symbol == asCoroutineFriendlyFuncSymbol) {
            expression.extensionReceiver?.let {
                log("Extension receiver: $it")
                log("Extension type: ${it.type}")
                log("Extension type fq: ${it.type.classFqName}")
                listPublicMethods(it.type)
            }
        }
        expression.acceptChildren(this, null)
    }

    private fun listPublicMethods(type: IrType) {
        val simpleType = type.type as? IrSimpleType ?: return
        val classSymbol = simpleType.classifier as? IrClassSymbol ?: return
        val irClass: IrClass = classSymbol.owner

        val publicMethods = irClass.declarations.filterIsInstance<IrFunction>()
            .filter { it.visibility == DescriptorVisibilities.PUBLIC }
            .filter { !it.isFakeOverride }
            .filter { it !is IrConstructor }

        // Print out or log the names of the public methods.
        publicMethods.forEach { method ->
            log("Method: ${method.signatureString(compatibleMode = true)}")
        }
    }

    /*fun addGeneratedClassToModule(
        pluginContext: IrPluginContext,
        packageFragment: IrPackageFragment?,
        originalType: IrClass,
    ): Pair<IrFile, IrClass> {
        // Generate the alternative class (e.g., RepositoryAsCoroutineFriendly).
        val generatedClass = generateSuspendedAlternativeClass(pluginContext, originalType)

        // Define the target package for your generated classes.
        val targetPackage = FqName("io.github.rastiehaiev")
        // Create a synthetic IR file for that package.
        val syntheticFile = createSyntheticIrFile(packageFragment)
        // Add your generated class to the file declarations.
        syntheticFile.declarations.add(generatedClass)

        // Register the synthetic file in the module fragment.
        moduleFragment.files.add(syntheticFile)
        return syntheticFile to generatedClass
    }

    fun createSyntheticIrFile(
        packageFragment: IrPackageFragment?,
    ): IrFile {
        // A dummy file entry acts as the "source" reference for the synthetic file.
        val fileEntry = DummySourceBasedFileEntryImpl("SyntheticFile.kt")
        // Retrieve the package fragment from the module descriptor.

        // Create and return the synthetic IR file.
        return IrFileImpl(fileEntry, packageFragment!!.packageFragmentDescriptor)
    }

    fun generateSuspendedAlternativeClass(
        pluginContext: IrPluginContext,
        originalType: IrClass,
    ): IrClass {
        // Get the IR class of the original type.
        val originalClassSymbol = originalType.symbol
            ?: error("Original type is not an IrClass")
        val originalName = originalClassSymbol.owner.name.asString() // e.g., "Repository"
        // Append a suffix for the new class name.
        val newClassName = "${originalName}AsCoroutineFriendly"

        // Create a new IR class.
        val newClass = pluginContext.irFactory.buildClass {
            name = Name.identifier(newClassName)
            kind = ClassKind.CLASS
            visibility = DescriptorVisibilities.PUBLIC
        }.apply {
            createImplicitParameterDeclarationWithWrappedDescriptor()
        }

        // Create a suspend function "hello" inside the generated class.
        val helloFunction = pluginContext.irFactory.buildFun {
            name = Name.identifier("hello")
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.PUBLIC
            isSuspend = true
        }.apply {
            parent = newClass
            // Create a simple empty body, e.g., using a block body with dummy offsets.
            body = pluginContext.irFactory.createBlockBody(0, 0)
        }

        // Add the function to the new class.
        newClass.declarations.add(helloFunction)

        return newClass
    }*/
}

private fun log(message: String) {
    File("/Users/roman/dev/project/personal/suspensify-kotlin-compiler-plugin").resolve("output.txt")
        .also { if (!it.exists()) Files.createFile(it.toPath()) }
        .appendText("\n$message")
}

private class DummySourceBasedFileEntryImpl(
    override val name: String,
) : AbstractIrFileEntry() {
    override val lineStartOffsets: IntArray = intArrayOf(0)
    override val maxOffset: Int = 0
}
