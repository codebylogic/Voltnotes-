package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shivam.vaultnotes.data.codec.NoteHtmlCodec
import com.shivam.vaultnotes.data.model.ImageBlock
import com.shivam.vaultnotes.data.model.ParagraphBlock
import com.shivam.vaultnotes.data.model.TableBlock
import com.shivam.vaultnotes.data.model.ToggleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VaultNotes", appName)
    }

    @Test
    fun `lossless HTML codec roundtrip preserves blocks, math, styles, tables, and toggles`() {
        val sampleHtml = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="note-id" content="550e8400-e29b-41d4-a716-446655440000">
              <title>Physics Notes</title>
            </head>
            <body>
              <h1 class="note-title">Physics Notes</h1>
              <div class="note-body">
                <p>Kinetic energy: <span class="math" data-tex="K = \frac{1}{2}mv^2">K = ½mv²</span></p>
                <p><span style="color:#DC2626;font-weight:bold;font-size:18px;">Important</span> <span style="background-color:rgba(255,235,59,0.45);">highlight</span>.</p>
                <img src="assets/img_20261116_113000.jpg" style="max-width:100%;border-radius:12px;">
                <table>
                  <colgroup><col style="width:140px"><col style="width:180px"></colgroup>
                  <tbody><tr><td>Mass</td><td>5 kg</td></tr></tbody>
                </table>
                <details open><summary>Derivation</summary><p>Work W = F * d</p></details>
              </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = NoteHtmlCodec.parseHtml(sampleHtml)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", parsed.id)
        assertEquals("Physics Notes", parsed.title)
        assertEquals(5, parsed.blocks.size)

        val p1 = parsed.blocks[0] as ParagraphBlock
        assertEquals(1, p1.content.mathSpans.size)
        assertEquals("K = \\frac{1}{2}mv^2", p1.content.mathSpans[0].latex)

        val p2 = parsed.blocks[1] as ParagraphBlock
        assertTrue(p2.content.spans.any { it.isBold && it.textColorHex == "#DC2626" && it.fontSizeSp == 18 })
        assertTrue(p2.content.spans.any { it.highlightColorHex == "rgba(255,235,59,0.45)" })

        val img = parsed.blocks[2] as ImageBlock
        assertEquals("assets/img_20261116_113000.jpg", img.relativePath)

        val table = parsed.blocks[3] as TableBlock
        assertEquals(listOf(140, 180), table.columnWidthsDp)
        assertEquals("Mass", table.rows[0][0].text)
        assertEquals("5 kg", table.rows[0][1].text)

        val toggle = parsed.blocks[4] as ToggleBlock
        assertTrue(toggle.isExpanded)
        assertEquals("Derivation", toggle.summary.text)

        val reserialized = NoteHtmlCodec.serializeToHtml(parsed)
        val reparsed = NoteHtmlCodec.parseHtml(reserialized)
        assertEquals(parsed.id, reparsed.id)
        assertEquals(parsed.title, reparsed.title)
        assertEquals(parsed.blocks.size, reparsed.blocks.size)
    }
}
