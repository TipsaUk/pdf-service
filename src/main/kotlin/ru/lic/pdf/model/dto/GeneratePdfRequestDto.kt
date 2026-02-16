package ru.lic.pdf.model.dto

data class GeneratePdfRequestDto(
    val baseFile: String,
    val files: List<String>,
    val outputFile: String
)