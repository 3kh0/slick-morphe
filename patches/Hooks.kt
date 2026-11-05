package slick.android

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Shared helpers for the patches in this package.

internal fun BytecodePatchContext.method(
    type: String,
    name: String,
    match: (MutableMethod) -> Boolean = { true },
) = (mutableClassDefByOrNull(type) ?: sibling(type, name)).methods.single { it.name == name && match(it) }

private fun BytecodePatchContext.sibling(
    type: String,
    name: String,
): MutableClass {
    val outer = type.substringBefore('$') + '$'
    val found = mutableListOf<ClassDef>()
    classDefForEach { c -> if (c.type.startsWith(outer) && c.methods.any { it.name == name }) found += c }
    check(found.size == 1) { "Could not find class: $type (${found.size} classes in $outer declare $name: ${found.map { it.type }})" }
    return mutableClassDefBy(found[0])
}

internal val MutableMethod.code get() = implementation!!.instructions
internal val MutableMethod.registers get() = implementation!!.registerCount

internal fun MutableMethod.indexed(match: (Instruction) -> Boolean) = code.withIndex().filter { match(it.value) }

internal fun MutableMethod.indexed(op: Opcode) = indexed { it.opcode == op }

internal val Instruction.ref get() = (this as? ReferenceInstruction)?.reference
internal val Instruction.field get() = ref as? FieldReference
internal val Instruction.cast get() = ((this as ReferenceInstruction).reference as TypeReference).type
internal val Instruction.reg get() = (this as OneRegisterInstruction).registerA

internal fun <T> List<T>.one(): T {
    check(size == 1)
    return first()
}

/** Calls a static Slick hook with the single register [r]. */
internal fun call(
    r: String,
    hook: String,
) = "invoke-static/range {$r .. $r}, Lslick/android/$hook"

/** Passes [r] through a Slick hook and stores the result back, optionally re-cast. */
internal fun wrap(
    r: String,
    hook: String,
    cast: String? = null,
) = "${call(r, hook)}\nmove-result-object $r" + (cast?.let { "\ncheck-cast $r, $it" } ?: "")

/** At entry, runs [hook] into v0 and takes [exit] when it is non-zero, else falls through to stock code. */
internal fun MutableMethod.intercept(
    hook: String,
    exit: String,
) = addInstructions(0, "$hook\nif-eqz v0, :stock\n$exit\n:stock\nnop")

/** Replaces return-object at [i] with a hook that transforms and re-casts the returned register. */
internal fun MutableMethod.wrapReturn(
    i: Int,
    r: Int,
    hook: String,
    type: String,
) {
    replaceInstruction(i, call("v$r", "$hook(Ljava/lang/Object;)Ljava/lang/Object;"))
    addInstructions(i + 1, "move-result-object v$r\ncheck-cast v$r, $type\nreturn-object v$r")
}
