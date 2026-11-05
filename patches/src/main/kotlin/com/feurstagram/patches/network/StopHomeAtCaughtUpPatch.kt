package com.feurstagram.patches.network

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val BRIDGE = "Lcom/feurstagram/extension/HomeFeedEnd;"
private const val BLOCK = "Lcom/feurstagram/extension/Block;"

@Suppress("unused")
val stopHomeAtCaughtUpPatch = bytecodePatch(
    name = "Stop Home at caught up",
    description = "Keeps the ranked Home feed and its caught-up card, and stops the rows below it. " +
        "Independent runtime toggle, on by default. Does not select Following mode.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    extendWith(EXTENSION)

    execute {
        val classes = mutableListOf<ClassDef>()
        classDefForEach { classes += it }
        val target = try {
            findHomeFeedEndTargets(classes)
        } catch (failure: IllegalStateException) {
            throw PatchException(failure.message ?: "Home row layout changed")
        }
        fun mutable(method: Method) = mutableClassDefBy(method.definingClass).methods.single {
            it.name == method.name && it.returnType == method.returnType &&
                it.parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)
        }
        val bridges = mutableClassDefBy(BRIDGE).methods.associateBy { it.name }
        val bodies = mapOf(
            "isHomeAdapter" to "instance-of p0, p0, ${target.home}\nreturn p0",
            "feedSource" to "check-cast p0, ${target.feed.definingClass}\n" +
                "iget-object p0, p0, ${target.feed}\ncheck-cast p0, ${target.source.definingClass}\n" +
                "iget-object p0, p0, ${target.source}\nreturn-object p0",
            "rowBuilder" to "check-cast p0, ${target.builder.definingClass}\n" +
                "iget-object p0, p0, ${target.builder}\nreturn-object p0",
            "rowCount" to "check-cast p0, ${target.count.definingClass}\n" +
                "iget p0, p0, ${target.count}\nreturn p0",
            "rowItems" to "check-cast p0, ${target.rows.definingClass}\n" +
                "iget-object p0, p0, ${target.rows}\nreturn-object p0",
            "rowModel" to "check-cast p0, ${target.model.definingClass}\n" +
                "iget-object p0, p0, ${target.model}\nreturn-object p0",
            "isEndCard" to "instance-of p0, p0, ${target.marker}\nreturn p0",
            "setRowCount" to "check-cast p0, ${target.count.definingClass}\n" +
                "iput p1, p0, ${target.count}\nreturn-void",
        )
        // Resolve every bridge before writing any changes: a changed build fails
        // visibly during patching instead of shipping a toggle that does nothing.
        bodies.keys.forEach { if (bridges[it] == null) throw PatchException("Missing Home boundary bridge: $it") }
        bodies.forEach { (name, body) -> bridges.getValue(name).addInstructions(0, body) }

        mutable(target.publish).addInstructions(
            0, "invoke-static/range { p0 .. p0 }, $BLOCK->limitHomeRows(Ljava/lang/Object;)V",
        )
        // All three parameters are contiguous, even when the method has high
        // registers. Clamp the reused prefix before any diff snapshot is sent.
        mutable(target.diff).addInstructions(
            0,
            "invoke-static/range { p0 .. p2 }, " +
                "$BLOCK->limitHomeRowPrefix(Ljava/lang/Object;Ljava/lang/Object;I)I\nmove-result p2",
        )
    }
}
