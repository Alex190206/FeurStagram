package com.feurstagram.patches.network

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class HomeFeedEndTargetsTest {
    private val home = "Lfixture/RankedHome;"
    private val host = "Lfixture/BinderGroupAdapter;"
    private val feed = "Lfixture/Feed;"
    private val rows = "Lfixture/Rows;"
    private val row = "Lfixture/Row;"
    private val card = "Lfixture/CaughtUpCard;"
    private val item = "Lfixture/FeedItem;"
    private val kind = "Lfixture/FeedKind;"
    private val obj = "Ljava/lang/Object;"
    private val string = "Ljava/lang/String;"
    private val list = "Ljava/util/List;"
    private val pub = AccessFlags.PUBLIC.value
    private val feedField = ImmutableFieldReference(home, "feed", feed)
    private val sourceField = ImmutableFieldReference(feed, "source", string)
    private val builderField = ImmutableFieldReference(host, "rows", rows)
    private val countField = ImmutableFieldReference(rows, "active", "I")
    private val rowsField = ImmutableFieldReference(rows, "items", list)
    private val modelField = ImmutableFieldReference(row, "model", obj)

    private fun text(value: String): Instruction = ImmutableInstruction21c(Opcode.CONST_STRING, 7, ImmutableStringReference(value))
    private fun read(field: ImmutableFieldReference, a: Int = 0, b: Int = 1): Instruction =
        ImmutableInstruction22c(if (field.type == "I") Opcode.IGET else Opcode.IGET_OBJECT, a, b, field)
    private fun method(owner: String, name: String, params: List<String> = emptyList(), returns: String = "V", vararg code: Instruction): Method =
        ImmutableMethod(owner, name, params.map { ImmutableMethodParameter(it, null, null) }, returns, pub, null, null,
            ImmutableMethodImplementation(10, code.toList(), null, null))
    private fun type(owner: String, fields: List<ImmutableFieldReference> = emptyList(), methods: List<Method> = emptyList(), parent: String = obj): ClassDef =
        ImmutableClassDef(owner, pub, parent, null, null, null,
            fields.map { ImmutableField(it.definingClass, it.name, it.type, pub, null, null, null) }, methods)

    private fun fixture(): List<ClassDef> {
        val getId = ImmutableMethodReference("Lfixture/Binder;", "getIdentifier", listOf("I", obj, obj), "I")
        return listOf(
            type(home, listOf(feedField), listOf(method(home, "build", code = arrayOf(text("MainfeedAdapter.buildModels")))), host),
            type(host, listOf(builderField), listOf(
                method(host, "publish", code = arrayOf(text("BinderGroupAdapter.submitToDiffer"), text("BinderGroupAdapter.notifyDataSetChanged"))),
                method(host, "diff", listOf("Landroid/content/res/Configuration;", "I"), "V",
                    text("smartUpdate"), text("smartUpdateSync"), read(builderField), read(countField, 1, 0), read(rowsField, 2, 0),
                    ImmutableInstruction21c(Opcode.CHECK_CAST, 3, ImmutableTypeReference(row)), read(modelField, 4, 3),
                    ImmutableInstruction35c(Opcode.INVOKE_INTERFACE, 4, 0, 1, 4, 5, 0, getId)),
            )),
            type(feed, listOf(sourceField)),
            type(rows, listOf(countField, rowsField)),
            type(row, listOf(modelField, ImmutableFieldReference(row, "state", obj))),
            type(card),
            type("Lfixture/LoadMore;", methods = listOf(method("Lfixture/LoadMore;", "endRule", listOf(home), "Z",
                read(feedField), read(sourceField, 0, 0), text("homecoming_following")))),
            type("Lfixture/EndCardBinder;", methods = listOf(method("Lfixture/EndCardBinder;", "bindView", code = arrayOf(
                text("null cannot be cast to non-null type com.instagram.mainfeed.unconnectedcontent.demarcator.EndOfFeedUnitDemarcatorHolder"),
                text("null cannot be cast to non-null type com.instagram.feed.feeditem.EndOfFeedDemarcatorUnit"),
                ImmutableInstruction21c(Opcode.CHECK_CAST, 0, ImmutableTypeReference(card)),
            )))),
            type(item, listOf(ImmutableFieldReference(item, "clips", "Lcom/instagram/api/schemas/ClipsNetego;"),
                ImmutableFieldReference(item, "end", card), ImmutableFieldReference(item, "kind", kind))),
            type(kind, methods = listOf(method(kind, "<clinit>", code = arrayOf(text("END_OF_FEED_DEMARCATOR")))), parent = "Ljava/lang/Enum;"),
        )
    }

    @Test fun discoversModelRatherThanStateAndAllPublicationPaths() {
        val found = findHomeFeedEndTargets(fixture())
        assertEquals(home, found.home)
        assertEquals("publish", found.publish.name)
        assertEquals("diff", found.diff.name)
        assertEquals(modelField, found.model)
        assertEquals(countField, found.count)
        assertEquals(rowsField, found.rows)
        assertEquals(sourceField, found.source)
        assertEquals(card, found.marker)
    }

    @Test fun unrelatedPublisherCannotBeSelected() {
        val extra = type("Lfixture/ExploreAdapter;", methods = listOf(method("Lfixture/ExploreAdapter;", "publish", code = arrayOf(
            text("BinderGroupAdapter.submitToDiffer"), text("BinderGroupAdapter.notifyDataSetChanged")))))
        assertEquals(host, findHomeFeedEndTargets(fixture() + extra).publish.definingClass)
    }

    @Test fun missingIncrementalPathRefusesInsteadOfShippingPartialCutoff() {
        val classes = fixture().map { if (it.type == host) type(host, listOf(builderField), it.methods.filter { m -> m.name != "diff" }) else it }
        assertThrows(IllegalStateException::class.java) { findHomeFeedEndTargets(classes) }
    }

    @Test fun ambiguousHomeBuilderRefuses() {
        val duplicate = type("Lfixture/AnotherHome;", methods = listOf(method("Lfixture/AnotherHome;", "build", code = arrayOf(text("MainfeedAdapter.buildModels")))))
        assertThrows(IllegalStateException::class.java) { findHomeFeedEndTargets(fixture() + duplicate) }
    }

    @Test fun missingProtocolMarkerRefuses() {
        val classes = fixture().map { if (it.type == kind) type(kind, parent = "Ljava/lang/Enum;") else it }
        assertThrows(IllegalStateException::class.java) { findHomeFeedEndTargets(classes) }
    }

    @Test fun missingModelFieldRefuses() {
        val classes = fixture().map { if (it.type == row) type(row) else it }
        assertThrows(IllegalStateException::class.java) { findHomeFeedEndTargets(classes) }
    }

    @Test fun genericFeedFieldRequiresItsObservedConcreteCast() {
        val generic = ImmutableFieldReference(home, "feed", obj)
        val classes = fixture().map {
            when (it.type) {
                home -> type(home, listOf(generic), it.methods.toList(), host)
                "Lfixture/LoadMore;" -> type(it.type, methods = listOf(method(it.type, "endRule", listOf(home), "Z",
                    read(generic), ImmutableInstruction21c(Opcode.CHECK_CAST, 0, ImmutableTypeReference(feed)),
                    read(sourceField, 0, 0), text("homecoming_following"))))
                else -> it
            }
        }
        assertEquals(generic, findHomeFeedEndTargets(classes).feed)
    }

    @Test fun renderedCardCanBeBuiltFromSharedResponseDetails() {
        val details = "Lfixture/EndDetails;"
        val detailsKind = "Lfixture/EndDetailsKind;"
        val classes = fixture().map {
            when (it.type) {
                item -> type(item, listOf(ImmutableFieldReference(item, "clips", "Lcom/instagram/api/schemas/ClipsNetego;"),
                    ImmutableFieldReference(item, "details", details), ImmutableFieldReference(item, "kind", kind)))
                card -> type(card, listOf(ImmutableFieldReference(card, "details", details)))
                else -> it
            }
        } + listOf(
            type(details, listOf(ImmutableFieldReference(details, "kind", detailsKind))),
            type(detailsKind, methods = listOf(method(detailsKind, "<clinit>", code = arrayOf(text("TOP_OF_FEED")))), parent = "Ljava/lang/Enum;"),
        )
        assertEquals(card, findHomeFeedEndTargets(classes).marker)
    }

    @Test fun privateActiveCountRefusesAnUnreachableBridge() {
        val classes = fixture().map {
            if (it.type != rows) it else ImmutableClassDef(rows, pub, obj, null, null, null,
                listOf(ImmutableField(rows, "active", "I", AccessFlags.PRIVATE.value, null, null, null),
                    ImmutableField(rows, "items", list, pub, null, null, null)), emptyList())
        }
        assertThrows(IllegalStateException::class.java) { findHomeFeedEndTargets(classes) }
    }

    @Test fun optionalReleasedApkHasTheCompleteExpectedLayout() {
        val path = System.getenv("FEURSTAGRAM_TEST_APK")
        assumeTrue("Set FEURSTAGRAM_TEST_APK to an Instagram/FeurStagram APK", path != null)
        val dex = DexFileFactory.loadDexContainer(File(path!!), Opcodes.getDefault())
        val classes = dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes }
        val found = findHomeFeedEndTargets(classes)
        assertNotEquals(found.model, found.count)
        assertEquals("Ljava/util/List;", found.rows.type)
        assertEquals("I", found.count.type)
        println("Home boundary fixture: $found")
    }
}
