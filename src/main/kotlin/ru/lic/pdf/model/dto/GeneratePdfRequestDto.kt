package ru.lic.pdf.model.dto

data class GeneratePdfRequestDto(
    val baseFile: String,
    val labels: List<LabelDto>,
    val outputFile: String
)

data class LabelDto(
    val nomenclature: String,
    val quantity: String,
    val unitOfMeasure: String,
    val orderNumber: String,
    val shipmentNumber: String
)
