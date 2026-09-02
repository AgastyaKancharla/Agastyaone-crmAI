package com.agastyaone.crmai.data.imaging

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class ImagingType(val id: String, val label: String) {
    RVG("RVG", "RVG (intraoral X-ray)"),
    OPG("OPG", "OPG (panoramic X-ray)"),
    CBCT("CBCT", "CBCT"),
    INTRAORAL_PHOTO("intraoralPhoto", "Intraoral photo");

    companion object {
        fun fromId(id: String): ImagingType = entries.firstOrNull { it.id == id } ?: RVG
    }
}

/**
 * `tenants/{clinicId}/imaging/{imageId}`. [toothNumber] is nullable - a full OPG or a
 * general intraoral photo isn't always tied to one tooth.
 */
data class ImagingRecord(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val toothNumber: String? = null,
    val type: String = ImagingType.RVG.id,
    val storageUrl: String = "",
    val capturedAt: Timestamp? = null,
    val uploadedByUid: String? = null,
    val notes: String? = null,
)
