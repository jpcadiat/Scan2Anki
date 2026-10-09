package com.scan2anki.ocr

import kotlinx.serialization.Serializable

@Serializable
data class CloudVisionResponse(
    val responses: List<VisionResponse> = emptyList(),
    val error: VisionError? = null,
)

@Serializable
data class VisionError(
    val code: Int? = null,
    val message: String = "",
    val status: String = "",
)

@Serializable
data class VisionResponse(
    val fullTextAnnotation: FullTextAnnotation? = null,
    val error: VisionStatus? = null,
)

@Serializable
data class VisionStatus(
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
data class FullTextAnnotation(val pages: List<VisionPage> = emptyList())

@Serializable
data class VisionPage(val blocks: List<VisionBlock> = emptyList())

@Serializable
data class VisionBlock(val paragraphs: List<VisionParagraph> = emptyList())

@Serializable
data class VisionParagraph(val words: List<VisionWord> = emptyList())

@Serializable
data class VisionWord(val boundingBox: BoundingBox? = null, val symbols: List<VisionSymbol> = emptyList())

@Serializable
data class VisionSymbol(val text: String = "")

@Serializable
data class BoundingBox(val vertices: List<Vertex> = emptyList())

@Serializable
data class Vertex(val x: Int? = null, val y: Int? = null)

@Serializable
data class VisionRequest(val requests: List<VisionRequestItem>)

@Serializable
data class VisionRequestItem(val image: VisionImage, val features: List<VisionFeature>)

@Serializable
data class VisionImage(val content: String)

@Serializable
data class VisionFeature(val type: String = "TEXT_DETECTION")
