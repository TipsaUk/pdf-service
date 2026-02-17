package ru.lic.pdf.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.lic.pdf.exception.PdfFileNotFoundException
import ru.lic.pdf.exception.PdfGenerationException
import ru.lic.pdf.model.dto.GeneratePdfRequestDto
import ru.lic.pdf.model.dto.LabelDto
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class PdfMergeService(
    @Value("\${pdf.output-file}") private val outputFile: String,
    @Value("\${pdf.font-file:}") private val fontFile: String
)  {

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
            val labelFont = loadLabelFont(resultDoc)
            val basePages = baseDoc.pages.toList()
            val labels = request.labels
            val maxSize = maxOf(basePages.size, labels.size)

            repeat(maxSize) { index ->
                if (index < basePages.size) {
                    resultDoc.importPage(basePages[index])
                }

                if (index < labels.size) {
                    addLabelPage(resultDoc, labels[index], labelFont)
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

    private fun addLabelPage(document: PDDocument, label: LabelDto, font: PDFont) {
        val pageWidth = LABEL_PAGE_WIDTH_MM * POINTS_PER_MM
        val pageHeight = LABEL_PAGE_HEIGHT_MM * POINTS_PER_MM
        val frameInset = 2f * POINTS_PER_MM
        val contentInset = frameInset + (2f * POINTS_PER_MM)
        val fontSize = 10f
        val lineHeight = 14f

        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        page.rotation = LABEL_PAGE_ROTATION_DEGREES
        document.addPage(page)

        PDPageContentStream(document, page).use { contentStream ->
            contentStream.setLineWidth(1f)
            contentStream.addRect(frameInset, frameInset, pageWidth - frameInset * 2, pageHeight - frameInset * 2)
            contentStream.stroke()

            val topStaticTextY = pageHeight - contentInset - fontSize
            drawCenteredLine(contentStream, font, fontSize, pageWidth, topStaticTextY, "Наш сайт diasgroup.ru")

            val shipmentY = contentInset
            val orderY = shipmentY + lineHeight
            drawCenteredLine(contentStream, font, fontSize, pageWidth, orderY, label.orderNumber)
            drawCenteredLine(contentStream, font, fontSize, pageWidth, shipmentY, label.shipmentNumber)

            val quantityAndUom = "${label.quantity} ${label.unitOfMeasure}".trim()
            val quantityAndUomY = orderY + lineHeight * 1.5f
            drawCenteredLine(contentStream, font, fontSize, pageWidth, quantityAndUomY, quantityAndUom)

            val maxTextWidth = pageWidth - contentInset * 2
            val nomenclatureStartY = topStaticTextY - lineHeight * 1.5f
            val availableHeight = nomenclatureStartY - quantityAndUomY - lineHeight
            val maxNomenclatureLines = (availableHeight / lineHeight).toInt().coerceAtLeast(1)
            val nomenclatureLines = wrapTextByWords(label.nomenclature, font, fontSize, maxTextWidth)
                .take(maxNomenclatureLines)

            nomenclatureLines.forEachIndexed { index, line ->
                val y = nomenclatureStartY - (index * lineHeight)
                drawCenteredLine(contentStream, font, fontSize, pageWidth, y, line)
            }
        }
    }

    private fun drawCenteredLine(
        contentStream: PDPageContentStream,
        font: PDFont,
        fontSize: Float,
        pageWidth: Float,
        y: Float,
        text: String
    ) {
        val safeText = text.trim()
        if (safeText.isBlank()) return

        val textWidth = (font.getStringWidth(safeText) / 1000f) * fontSize
        val x = ((pageWidth - textWidth) / 2f).coerceAtLeast(0f)

        contentStream.beginText()
        contentStream.setFont(font, fontSize)
        contentStream.newLineAtOffset(x, y)
        contentStream.showText(safeText)
        contentStream.endText()
    }

    private fun wrapTextByWords(text: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> {
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val candidate = if (currentLine.isBlank()) word else "$currentLine $word"
            val candidateWidth = (font.getStringWidth(candidate) / 1000f) * fontSize
            if (candidateWidth <= maxWidth || currentLine.isBlank()) {
                currentLine = candidate
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }

        if (currentLine.isNotBlank()) {
            lines.add(currentLine)
        }

        return lines
    }

    private fun loadLabelFont(document: PDDocument): PDFont {
        val candidatePaths = buildFontCandidates()
        val existingPath = candidatePaths.firstOrNull { path ->
            Files.exists(path) && Files.isRegularFile(path)
        } ?: throw PdfGenerationException(
            "Unable to find a TrueType font for PDF labels. Set 'pdf.font-file' in configuration. Checked: ${candidatePaths.joinToString()}"
        )

        Files.newInputStream(existingPath).use { fontStream ->
            return PDType0Font.load(document, fontStream)
        }
    }

    private fun buildFontCandidates(): List<Path> {
        val configured = fontFile.trim()
        val candidates = mutableListOf<Path>()
        if (configured.isNotBlank()) {
            candidates.add(Paths.get(configured))
        }

        val windowsDir = System.getenv("WINDIR")
        if (!windowsDir.isNullOrBlank()) {
            candidates.add(Paths.get(windowsDir, "Fonts", "arial.ttf"))
            candidates.add(Paths.get(windowsDir, "Fonts", "times.ttf"))
        }

        candidates.add(Paths.get("C:/Windows/Fonts/arial.ttf"))
        candidates.add(Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
        candidates.add(Paths.get("/usr/share/fonts/dejavu/DejaVuSans.ttf"))
        candidates.add(Paths.get("/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"))
        candidates.add(Paths.get("/Library/Fonts/Arial Unicode.ttf"))
        candidates.add(Paths.get("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"))

        return candidates.distinct()
    }

    companion object {
        private const val POINTS_PER_MM = 72f / 25.4f
        private const val LABEL_PAGE_WIDTH_MM = 120f
        private const val LABEL_PAGE_HEIGHT_MM = 75f
        private const val LABEL_PAGE_ROTATION_DEGREES = 90
    }

}
