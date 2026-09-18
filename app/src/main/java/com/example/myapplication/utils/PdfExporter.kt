package com.example.myapplication.utils

import android.content.Context
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import java.io.File

object PdfExporter {
    fun exportProjectToPdf(context: Context, projectName: String, content: String): File {
        val file = File(context.cacheDir, "$projectName.pdf")
        PdfWriter(file).use { writer ->
            PdfDocument(writer).use { pdfDoc ->
                Document(pdfDoc).use { doc ->
                    doc.add(Paragraph(projectName))
                    doc.add(Paragraph(content))
                }
            }
        }
        return file
    }
}
