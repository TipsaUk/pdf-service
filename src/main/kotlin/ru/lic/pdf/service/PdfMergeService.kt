package ru.lic.pdf.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.lic.pdf.exception.PdfFileNotFoundException
import ru.lic.pdf.exception.PdfGenerationException
import ru.lic.pdf.model.dto.GeneratePdfRequestDto
import ru.lic.pdf.model.dto.LabelDto
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@Service
class PdfMergeService(
    @Value("\${pdf.output-file}") private val outputFile: String
)  {

    companion object {
        private const val POINTS_PER_MM = 72f / 25.4f
        private const val LABEL_PAGE_WIDTH_MM = 75f
        private const val LABEL_PAGE_HEIGHT_MM = 120f
        private const val LABEL_PAGE_ROTATION_DEGREES = 90
    }

    fun generate(request: GeneratePdfRequestDto) {
        try {
            generateInternal(request)
        } catch (e: IOException) {
            throw PdfGenerationException("Failed to generate PDF", e)
        }
    }

    private fun generateInternal(request: GeneratePdfRequestDto) {
        val basePath = Paths.get(request.baseFile)
        val outputPath = if (request.outputFile.isBlank()) Paths.get(outputFile) else Paths.get(request.outputFile)

        if (!Files.exists(basePath)) {
            throw PdfFileNotFoundException(basePath.toString())
        }

        outputPath.parent?.let { Files.createDirectories(it) }

        // открываем базовый PDF
        val baseDoc = PDDocument.load(basePath.toFile())

        // создаем итоговый документ
        val resultDoc = PDDocument()

        try {
            val basePages = baseDoc.pages.toList()
            val labels = request.labels
            val maxSize = maxOf(basePages.size, labels.size)

            repeat(maxSize) { index ->
                if (index < basePages.size) {
                    resultDoc.importPage(basePages[index])
                }

                if (index < labels.size) {
                    addLabelPage(resultDoc, labels[index])
                }
            }

            // сохраняем итоговый PDF
            resultDoc.save(outputPath.toFile())
        } finally {
            // закрываем все документы после сохранения
            resultDoc.close()
            baseDoc.close()
        }
    }

    private fun addLabelPage(document: PDDocument, label: LabelDto) {
        val pageWidth = LABEL_PAGE_WIDTH_MM * POINTS_PER_MM
        val pageHeight = LABEL_PAGE_HEIGHT_MM * POINTS_PER_MM
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        page.rotation = LABEL_PAGE_ROTATION_DEGREES
        document.addPage(page)

        PDPageContentStream(document, page).use { contentStream ->
            contentStream.beginText()
            contentStream.setFont(PDType1Font.HELVETICA, 10f)
            contentStream.setLeading(16f)
            contentStream.newLineAtOffset(16f, pageHeight - 30f)
            contentStream.showText("Nomenclature: ${label.nomenclature}")
            contentStream.newLine()
            contentStream.showText("Quantity: ${label.quantity}")
            contentStream.newLine()
            contentStream.showText("Unit of measure: ${label.unitOfMeasure}")
            contentStream.newLine()
            contentStream.showText("Order number: ${label.orderNumber}")
            contentStream.newLine()
            contentStream.showText("Shipment number: ${label.shipmentNumber}")
            contentStream.endText()
        }
    }

}
