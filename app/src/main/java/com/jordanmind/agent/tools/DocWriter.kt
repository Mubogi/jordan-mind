package com.jordanmind.agent.tools

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Offline document writers.
 *
 * PDF is produced through PdfBox-Android. The OOXML formats (DOCX/XLSX/PPTX) are written directly
 * as ZIP containers of XML — no Apache POI, which keeps the APK far smaller and avoids POI's
 * dependence on `java.awt`, which Android does not ship.
 */
object DocWriter {

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    // ---------------------------------------------------------------- PDF

    fun writePdf(target: File, title: String, body: String, meta: String = "") {
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { document ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(
                com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
            )
            document.addPage(page)

            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
            val bold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
            val content = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)

            val margin = 56f
            val width = page.mediaBox.width - margin * 2
            var y = page.mediaBox.height - margin

            fun line(text: String, f: com.tom_roush.pdfbox.pdmodel.font.PDFont, size: Float, gap: Float) {
                if (y < margin + size) return
                content.beginText()
                content.setFont(f, size)
                content.newLineAtOffset(margin, y)
                content.showText(sanitize(text))
                content.endText()
                y -= gap
            }

            line(title, bold, 18f, 26f)
            if (meta.isNotBlank()) line(meta, font, 9f, 22f)
            line("", font, 10f, 10f)

            body.split("\n").forEach { raw ->
                wrap(raw, font, 11f, width).forEach { piece -> line(piece, font, 11f, 16f) }
            }

            content.close()
            FileOutputStream(target).use { document.save(it) }
        }
    }

    private fun sanitize(text: String): String = text
        .replace('\u2018', '\'').replace('\u2019', '\'')
        .replace('\u201C', '"').replace('\u201D', '"')
        .replace('\u2013', '-').replace('\u2014', '-')
        .replace('\u2026', '.')
        .map { if (it.code in 32..126) it else '?' }
        .joinToString("")

    private fun wrap(
        text: String,
        font: com.tom_roush.pdfbox.pdmodel.font.PDFont,
        size: Float,
        maxWidth: Float,
    ): List<String> {
        if (text.isBlank()) return listOf("")
        val words = sanitize(text).split(' ')
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            val w = runCatching { font.getStringWidth(candidate) / 1000f * size }.getOrDefault(0f)
            if (w > maxWidth && current.isNotEmpty()) {
                out.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                current.clear()
                current.append(candidate)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    // --------------------------------------------------------------- OOXML

    fun writeDocx(target: File, title: String, body: String, meta: String = "") {
        val paragraphs = buildString {
            append(paragraph(title, style = "Title"))
            if (meta.isNotBlank()) append(paragraph(meta, italic = true))
            body.split("\n").forEach { line ->
                append(paragraph(line))
            }
        }
        zip(
            target,
            mapOf(
                "[Content_Types].xml" to CONTENT_TYPES_DOCX,
                "_rels/.rels" to RELS_ROOT_DOCX,
                "word/document.xml" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs</w:body></w:document>""",
                "docProps/core.xml" to coreProps(title),
            ),
        )
    }

    private fun paragraph(text: String, style: String? = null, italic: Boolean = false): String {
        val safe = escape(text)
        val props = buildString {
            append("<w:rPr>")
            if (italic) append("<w:i/>")
            append("</w:rPr>")
        }
        val pPr = if (style != null) """<w:pPr><w:pStyle w:val="$style"/></w:pPr>""" else ""
        return """<w:p>$pPr<w:r>$props<w:t xml:space="preserve">$safe</w:t></w:r></w:p>"""
    }

    fun writeXlsx(target: File, sheetName: String, rows: List<List<String>>) {
        val sheet = buildString {
            rows.forEachIndexed { r, row ->
                append("<row r=\"${r + 1}\">")
                row.forEachIndexed { c, cell ->
                    append(
                        """<c r="${colName(c)}${r + 1}" t="inlineStr"><is><t xml:space="preserve">"""
                    )
                    append(escape(cell))
                    append("</t></is></c>")
                }
                append("</row>")
            }
        }
        val safeSheet = escape(sheetName.ifBlank { "Sheet1" })
        zip(
            target,
            mapOf(
                "[Content_Types].xml" to CONTENT_TYPES_XLSX,
                "_rels/.rels" to RELS_ROOT_XLSX,
                "xl/workbook.xml" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="$safeSheet" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$sheet</sheetData></worksheet>""",
            ),
        )
    }

    fun writePptx(target: File, title: String, bullets: List<String>) {
        val bulletXml = bullets.joinToString("") { line ->
            """<a:p><a:r><a:rPr lang="en-US"/><a:t>${escape(line)}</a:t></a:r></a:p>"""
        }
        zip(
            target,
            mapOf(
                "[Content_Types].xml" to CONTENT_TYPES_PPTX,
                "_rels/.rels" to RELS_ROOT_PPTX,
                "ppt/presentation.xml" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst></p:presentation>""",
                "ppt/_rels/presentation.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>""",
                "ppt/slides/slide1.xml" to
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree>
<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>
<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" b="1"/><a:t>${escape(title)}</a:t></a:r></a:p></p:txBody></p:sp>
<p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>$bulletXml</p:txBody></p:sp>
</p:spTree></p:cSld></p:sld>""",
            ),
        )
    }

    fun writeTxt(target: File, body: String) {
        target.writeText(body)
    }

    // -------------------------------------------------------------- helpers

    private fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun coreProps(title: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>${escape(title)}</dc:title><dc:creator>Jordan Mind</dc:creator>
<dcterms:created xsi:type="dcterms:W3CDTF">${stamp()}</dcterms:created></cp:coreProperties>"""

    private fun zip(target: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(target)).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
    }

    private const val CONTENT_TYPES_DOCX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>"""

    private const val RELS_ROOT_DOCX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>"""

    private const val CONTENT_TYPES_XLSX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private const val RELS_ROOT_XLSX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val CONTENT_TYPES_PPTX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>"""

    private const val RELS_ROOT_PPTX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
}
