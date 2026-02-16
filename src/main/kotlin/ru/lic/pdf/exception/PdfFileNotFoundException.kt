package ru.lic.pdf.exception

class PdfFileNotFoundException(path: String): RuntimeException("PDF file not found: $path")