package com.jordanmind.agent.data

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Text extraction from PDFs using PdfBox-Android. */
object PdfText {

    fun extract(file: File): String = PDDocument.load(file).use { document ->
        PDFTextStripper().getText(document)
    }
}
