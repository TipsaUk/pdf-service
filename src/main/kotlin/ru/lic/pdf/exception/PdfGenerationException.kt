package ru.lic.pdf.exception

class PdfGenerationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)