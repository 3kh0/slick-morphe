package slick.android

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val blankEmoji =
    bytecodePatch {
        dependsOn(modMenu)
        execute {
            val loader =
                findMethod("invokeSuspend") { method ->
                    method.definingClass.startsWith("Lslack/emoji/impl/routine/LoadEmojiRoutine\$") &&
                        method.parameterTypes == listOf("Ljava/lang/Object;") && method.returnType == "Ljava/lang/Object;" &&
                        method.implementation?.instructions?.any {
                            (it.ref as? StringReference)?.string == "Emoji name is blank"
                        } == true
                }
            // Match the loader's validation, never a shared exception helper or coroutine handler.
            val (index, check) =
                loader
                    .indexed {
                        val ref = it.ref as? MethodReference
                        it.opcode == Opcode.INVOKE_STATIC && ref?.definingClass?.startsWith("Lkotlin/text/StringsKt") == true &&
                            ref.name == "isBlank" && ref.parameterTypes == listOf("Ljava/lang/CharSequence;") && ref.returnType == "Z"
                    }.one()
            val arguments = check as FiveRegisterInstruction
            check(arguments.registerCount == 1) { "Unexpected emoji blank-check arguments" }
            loader.addInstructions(index, wrap("v${arguments.registerC}", "Emoji;->name(Ljava/lang/String;)Ljava/lang/String;"))
        }
    }
