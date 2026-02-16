package ru.lic.pdf.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.lic.pdf.exception.PdfFileNotFoundException
import ru.lic.pdf.exception.PdfGenerationException
import ru.lic.pdf.model.dto.GeneratePdfRequestDto
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@Service
class PdfMergeService(
    @Value("\${pdf.output-file}") private val outputFile: String
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

        Files.createDirectories(outputPath.parent)

        // открываем базовый PDF
        val baseDoc = PDDocument.load(basePath.toFile())
        // открываем все insert PDF и храним их в списке
        val insertDocs = request.files.map { file ->
            val path = Paths.get(file)
            if (!Files.exists(path)) throw PdfFileNotFoundException(path.toString())
            val doc = PDDocument.load(path.toFile())
            if (doc.numberOfPages == 0) throw PdfGenerationException("Empty PDF: $path")
            doc
        }

        // создаем итоговый документ
        val resultDoc = PDDocument()

        try {
            baseDoc.pages.forEachIndexed { index, basePage ->
                // копируем страницу из базового PDF
                resultDoc.importPage(basePage)

                // вставляем страницу из insert PDF, если есть
                if (index < insertDocs.size) {
                    resultDoc.importPage(insertDocs[index].getPage(0))
                }
            }

            // сохраняем итоговый PDF
            resultDoc.save(outputPath.toFile())
        } finally {
            // закрываем все документы после сохранения
            resultDoc.close()
            baseDoc.close()
            insertDocs.forEach { it.close() }
        }
    }

}