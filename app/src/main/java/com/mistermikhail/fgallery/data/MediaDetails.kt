package com.mistermikhail.fgallery.data

data class DetailField(
    val label: String,
    val value: String,
)

data class DetailSection(
    val title: String,
    val fields: List<DetailField>,
)

data class MediaDetails(
    val sections: List<DetailSection>,
)
