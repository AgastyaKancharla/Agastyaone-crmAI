package com.agastyaone.crmai.core

import com.agastyaone.crmai.data.auth.AuthRepository
import com.agastyaone.crmai.data.billing.InvoiceRepository
import com.agastyaone.crmai.data.charting.ChartingRepository
import com.agastyaone.crmai.data.charting.TreatmentPlanRepository
import com.agastyaone.crmai.data.functions.CloudFunctionsRepository
import com.agastyaone.crmai.data.imaging.ImagingRepository
import com.agastyaone.crmai.data.insurance.InsuranceClaimRepository
import com.agastyaone.crmai.data.patients.PatientRepository
import com.agastyaone.crmai.data.scheduling.ScheduleRepository
import com.agastyaone.crmai.data.storage.ClaimDocumentUploader
import com.agastyaone.crmai.data.storage.ImageUploader
import com.agastyaone.crmai.data.storage.SignatureUploader
import com.agastyaone.crmai.data.tenant.TenantRepository

/**
 * Deliberately minimal manual DI for this phase - a Hilt/Koin graph is overkill
 * for the handful of singletons this app needs. Revisit once module count grows.
 */
object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val chartingRepository: ChartingRepository by lazy { ChartingRepository() }
    val claimDocumentUploader: ClaimDocumentUploader by lazy { ClaimDocumentUploader() }
    val cloudFunctionsRepository: CloudFunctionsRepository by lazy { CloudFunctionsRepository() }
    val imageUploader: ImageUploader by lazy { ImageUploader() }
    val imagingRepository: ImagingRepository by lazy { ImagingRepository() }
    val insuranceClaimRepository: InsuranceClaimRepository by lazy { InsuranceClaimRepository() }
    val invoiceRepository: InvoiceRepository by lazy { InvoiceRepository() }
    val patientRepository: PatientRepository by lazy { PatientRepository() }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository() }
    val signatureUploader: SignatureUploader by lazy { SignatureUploader() }
    val tenantRepository: TenantRepository by lazy { TenantRepository() }
    val treatmentPlanRepository: TreatmentPlanRepository by lazy { TreatmentPlanRepository() }
}
