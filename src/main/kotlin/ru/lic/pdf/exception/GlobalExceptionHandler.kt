package ru.lic.pdf.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PdfFileNotFoundException::class)
    fun handleNotFound(ex: PdfFileNotFoundException): ResponseEntity<ApiError> {
        log.error("File not found", ex)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError(ex.message!!))
    }


    @ExceptionHandler(PdfGenerationException::class)
    fun handleGeneration(ex: PdfGenerationException): ResponseEntity<ApiError> {
        log.error("PDF generation failed", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(ex.message!!))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(ex: RuntimeException): ResponseEntity<ApiError> {
        log.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(ex.message ?: "Unexpected error"))
    }

}

data class ApiError(
    val message: String
)