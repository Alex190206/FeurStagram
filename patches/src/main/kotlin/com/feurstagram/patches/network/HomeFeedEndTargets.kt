/*
 * Home scoping and marker discovery adapted from HushGram, GPLv3.
 * Copyright 2026 HushGram contributors https://github.com/SysAdminDoc/HushGram
 */
package com.feurstagram.patches.network

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal data class HomeFeedEndTargets(
    val home: String,
    val publish: Method,
    val diff: Method,
    val feed: FieldReference,
    val source: FieldReference,
    val builder: FieldReference,
    val count: FieldReference,
    val rows: FieldReference,
    val model: FieldReference,
    val marker: String,
)

/** Pure DEX discovery, also exercised against stand-ins and an optional APK fixture. */
internal fun findHomeFeedEndTargets(classes: Iterable<ClassDef>): HomeFeedEndTargets {
    val byType = classes.associateBy { it.type }
    val methods = byType.values.flatMap { it.methods }
    fun refuse(detail: String): Nothing = error("Stop Home at caught up: $detail")
    fun <T> List<T>.one(detail: String): T = singleOrNull() ?: refuse("$detail (found $size)")
    fun hierarchy(type: String): List<String> = generateSequence(type) { byType[it]?.superclass }.toList()
    fun Method.fields(opcode: Opcode): List<FieldReference> = code().filter { it.opcode == opcode }
        .mapNotNull { it.reference() as? FieldReference }.distinctBy { it.toString() }

    val home = methods.filter { it.loads("MainfeedAdapter.buildModels") }
        .one("expected one Home model builder").definingClass
    val parents = hierarchy(home)
    val publish = methods.filter {
        it.definingClass in parents && it.parameterTypes.isEmpty() && it.returnType == "V" &&
            !AccessFlags.STATIC.isSet(it.accessFlags) &&
            it.loads("BinderGroupAdapter.submitToDiffer") && it.loads("BinderGroupAdapter.notifyDataSetChanged")
    }.one("expected one Home row publisher")
    val diff = byType.getValue(publish.definingClass).methods.filter {
        it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/res/Configuration;", "I") &&
            !AccessFlags.STATIC.isSet(it.accessFlags) &&
            it.returnType == "V" && it.loads("smartUpdate") && it.loads("smartUpdateSync")
    }.one("expected one full/incremental diff publisher")

    // A Home load-more check reads adapter.feed.source before comparing it with
    // Following's protocol name. This excludes Profile/Explore, and avoids a
    // guessed String field on a feed that may have several of them.
    val sourcePaths = methods.filter {
        it.returnType == "Z" && it.parameterTypes.map(CharSequence::toString) == listOf(home) &&
            it.loads("homecoming_following")
    }.flatMap { method ->
        val code = method.code()
        code.indices.mapNotNull { i ->
            val feed = code[i].takeIf { it.opcode == Opcode.IGET_OBJECT }?.reference() as? FieldReference
                ?: return@mapNotNull null
            val cast = code.getOrNull(i + 1)?.takeIf { it.opcode == Opcode.CHECK_CAST }
            val feedType = (cast?.reference() as? TypeReference)?.type ?: feed.type
            val next = code.getOrNull(i + if (cast == null) 1 else 2) ?: return@mapNotNull null
            val source = next.takeIf { it.opcode == Opcode.IGET_OBJECT }?.reference() as? FieldReference
                ?: return@mapNotNull null
            if (feed.definingClass !in parents || source.definingClass != feedType || source.type != "Ljava/lang/String;" ||
                (cast != null && (cast as OneRegisterInstruction).registerA != (code[i] as TwoRegisterInstruction).registerA) ||
                (code[i] as TwoRegisterInstruction).registerA != (next as TwoRegisterInstruction).registerB
            ) null else feed to source
        }
    }.distinctBy { "${it.first} ${it.second}" }
    val (feed, source) = sourcePaths.one("expected one Home feed/source path")

    // In the publisher, the active count and row list belong to the same row
    // builder. The other List is the already published diff snapshot.
    val builderPaths = diff.fields(Opcode.IGET_OBJECT).filter { it.definingClass == publish.definingClass }
        .mapNotNull { field ->
            val count = diff.fields(Opcode.IGET).filter { it.definingClass == field.type && it.type == "I" }.singleOrNull()
            val rows = diff.fields(Opcode.IGET_OBJECT).filter { it.definingClass == field.type && it.type == "Ljava/util/List;" }.singleOrNull()
            if (count == null || rows == null) null else Triple(field, count, rows)
        }
    val (builder, count, rows) = builderPaths.one("expected one active row count/list")

    // BinderGroup.getIdentifier(int viewType, Object model, Object state).
    // Read the model argument's actual field, not the adjacent state field.
    val code = diff.code()
    val models = code.indices.mapNotNull { at ->
        val call = code[at].reference() as? MethodReference ?: return@mapNotNull null
        if (call.name != "getIdentifier" || call.parameterTypes.map(CharSequence::toString) !=
            listOf("I", "Ljava/lang/Object;", "Ljava/lang/Object;") || call.returnType != "I"
        ) return@mapNotNull null
        val invoke = code[at] as? FiveRegisterInstruction ?: refuse("getIdentifier changed its invocation layout")
        val register = invoke.registerE
        val load = (at - 1 downTo 0).firstOrNull {
            code[it].opcode.setsRegister() && (code[it] as? OneRegisterInstruction)?.registerA == register
        } ?: refuse("publisher doesn't load its model argument")
        (code[load].takeIf { it.opcode == Opcode.IGET_OBJECT }?.reference() as? FieldReference)
            ?.takeIf { it.type == "Ljava/lang/Object;" }
    }.distinctBy { it.toString() }
    val model = models.one("expected one row model field")
    if (code.none { it.opcode == Opcode.CHECK_CAST && (it.reference() as? TypeReference)?.type == model.definingClass }) {
        refuse("publisher doesn't read row specs of the model's class")
    }

    // HushGram finds the END_OF_FEED_DEMARCATOR's rendered model from this
    // binder's kept Kotlin cast messages; enum and JSON names are not confused.
    val unit = "null cannot be cast to non-null type com.instagram.feed.feeditem.EndOfFeedDemarcatorUnit"
    val holder = "null cannot be cast to non-null type com.instagram.mainfeed.unconnectedcontent.demarcator.EndOfFeedUnitDemarcatorHolder"
    val bind = methods.filter { it.name == "bindView" && it.loads(unit) && it.loads(holder) }
        .one("expected one end-of-feed card binder")
    val bindCode = bind.code()
    val named = bindCode.indexOfFirst { (it.reference() as? StringReference)?.string == unit }
    val marker = bindCode.drop(named + 1).firstOrNull { it.opcode == Opcode.CHECK_CAST }
        ?.let { (it.reference() as TypeReference).type } ?: refuse("end card binder no longer casts its model")
    val items = byType.values.filter { candidate ->
        candidate.fields.any { it.type == "Lcom/instagram/api/schemas/ClipsNetego;" }
    }.one("expected one feed item holding ClipsNetego")
    val kinds = items.fields.filter { !AccessFlags.STATIC.isSet(it.accessFlags) }.mapNotNull { byType[it.type] }
        .filter { it.superclass == "Ljava/lang/Enum;" && it.methods.any { method -> method.loads("END_OF_FEED_DEMARCATOR") } }
    kinds.one("expected one feed kind enum naming END_OF_FEED_DEMARCATOR")
    // Some releases store the card itself; 454 stores its details and builds the
    // rendered unit later. In that case both must hold the same details class,
    // whose kind enum names TOP_OF_FEED (HushGram's caught-up-card discriminator).
    if (items.fields.none { it.type == marker }) {
        val itemFields = items.fields.map { it.type }.toSet()
        val details = byType[marker]?.fields?.toList().orEmpty().filter { field ->
            field.type in itemFields && byType[field.type]?.fields?.any { kindField ->
                val enum = byType[kindField.type]
                enum?.superclass == "Ljava/lang/Enum;" && enum.methods.any { it.loads("TOP_OF_FEED") }
            } == true
        }
        details.toList().one("expected rendered end card and feed item to share caught-up details")
    }

    // Generated bridges may access only public classes and instance fields.
    for (type in listOf(home, marker, publish.definingClass, feed.definingClass, source.definingClass, builder.type, model.definingClass)) {
        if (byType[type]?.let { AccessFlags.PUBLIC.isSet(it.accessFlags) } != true) refuse("$type isn't public")
    }
    for (field in listOf(feed, source, builder, count, rows, model)) {
        val declared = byType[field.definingClass]?.fields?.singleOrNull { it.name == field.name && it.type == field.type }
        if (declared == null || !AccessFlags.PUBLIC.isSet(declared.accessFlags) || AccessFlags.STATIC.isSet(declared.accessFlags)) {
            refuse("$field isn't a public instance field")
        }
        if (field == count && AccessFlags.FINAL.isSet(declared.accessFlags)) refuse("$field cannot be capped")
    }
    return HomeFeedEndTargets(home, publish, diff, feed, source, builder, count, rows, model, marker)
}

private fun Method.code(): List<Instruction> = implementation?.instructions?.toList().orEmpty()
private fun Instruction.reference() = (this as? ReferenceInstruction)?.reference
private fun Method.loads(value: String): Boolean = code().any {
    (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
        (it.reference() as? StringReference)?.string == value
}
