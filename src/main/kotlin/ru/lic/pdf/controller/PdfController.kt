package ru.lic.pdf.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.lic.pdf.model.dto.GeneratePdfRequestDto
import ru.lic.pdf.service.PdfMergeService

@RestController
@RequestMapping("/pdf")
class PdfController(
    private val pdfMergeService: PdfMergeService
) {

    @PostMapping("/generate")
    fun generate(
        @RequestBody request: GeneratePdfRequestDto
    ): ResponseEntity<Void> {
        pdfMergeService.generate(request)
        return ResponseEntity.ok().build()
    }

}