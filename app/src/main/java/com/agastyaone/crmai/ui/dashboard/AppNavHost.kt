package com.agastyaone.crmai.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.auth.SessionState
import com.agastyaone.crmai.ui.charting.ChartingDetailScreen
import com.agastyaone.crmai.ui.charting.ChartingListScreen
import com.agastyaone.crmai.ui.charting.TreatmentPlanApprovalScreen
import com.agastyaone.crmai.ui.charting.TreatmentPlanBuilderScreen
import com.agastyaone.crmai.ui.charting.TreatmentPlanListScreen
import com.agastyaone.crmai.ui.imaging.ImageComparisonScreen
import com.agastyaone.crmai.ui.imaging.ImageGalleryScreen
import com.agastyaone.crmai.ui.imaging.ImageUploadScreen
import com.agastyaone.crmai.ui.patients.DataRequestsScreen
import com.agastyaone.crmai.ui.patients.IntakeFlowScreen
import com.agastyaone.crmai.ui.patients.PatientClinicalEditScreen
import com.agastyaone.crmai.ui.patients.PatientDetailScreen
import com.agastyaone.crmai.ui.patients.PatientFormScreen
import com.agastyaone.crmai.ui.patients.PatientListScreen
import com.agastyaone.crmai.ui.scheduling.AppointmentDetailScreen
import com.agastyaone.crmai.ui.scheduling.CalendarScreen
import com.agastyaone.crmai.ui.scheduling.WaitlistScreen
import com.agastyaone.crmai.ui.scheduling.WalkInScreen

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_INVITE_STAFF = "inviteStaff"
private const val ROUTE_PATIENT_LIST = "patients"
private const val ROUTE_PATIENT_ADD = "patients/add"
private const val ARG_PATIENT_ID = "patientId"
private const val ROUTE_PATIENT_DETAIL = "patients/{$ARG_PATIENT_ID}"
private const val ROUTE_PATIENT_EDIT_DEMOGRAPHICS = "patients/{$ARG_PATIENT_ID}/editDemographics"
private const val ROUTE_PATIENT_EDIT_CLINICAL = "patients/{$ARG_PATIENT_ID}/editClinical"
private const val ROUTE_PATIENT_INTAKE = "patients/{$ARG_PATIENT_ID}/intake"
private const val ROUTE_DATA_REQUESTS = "dataRequests"
private const val ROUTE_CALENDAR = "calendar"
private const val ROUTE_WALK_IN = "calendar/walkIn"
private const val ROUTE_WALK_IN_PATIENT_ADD = "calendar/walkIn/add"
private const val ROUTE_WAITLIST = "calendar/waitlist"
private const val ARG_APPOINTMENT_ID = "appointmentId"
private const val ROUTE_APPOINTMENT_DETAIL = "calendar/appointments/{$ARG_APPOINTMENT_ID}"
private const val ROUTE_CHARTING_LIST = "patients/{$ARG_PATIENT_ID}/chartings"
private const val ARG_CHARTING_ID = "chartingId"
private const val ROUTE_CHARTING_DETAIL = "chartings/{$ARG_CHARTING_ID}"
private const val ROUTE_TREATMENT_PLAN_LIST = "patients/{$ARG_PATIENT_ID}/treatmentPlans"
private const val ROUTE_TREATMENT_PLAN_BUILDER = "patients/{$ARG_PATIENT_ID}/treatmentPlans/new"
private const val ARG_PLAN_ID = "planId"
private const val ROUTE_TREATMENT_PLAN_APPROVAL = "patients/{$ARG_PATIENT_ID}/treatmentPlans/{$ARG_PLAN_ID}"
private const val ROUTE_IMAGING_GALLERY = "patients/{$ARG_PATIENT_ID}/imaging"
private const val ROUTE_IMAGING_UPLOAD = "patients/{$ARG_PATIENT_ID}/imaging/add"
private const val ARG_IMAGE_ID_A = "imageIdA"
private const val ARG_IMAGE_ID_B = "imageIdB"
private const val ROUTE_IMAGING_COMPARE =
    "patients/{$ARG_PATIENT_ID}/imaging/compare/{$ARG_IMAGE_ID_A}/{$ARG_IMAGE_ID_B}"

private fun patientDetailRoute(patientId: String) = "patients/$patientId"
private fun patientEditDemographicsRoute(patientId: String) = "patients/$patientId/editDemographics"
private fun patientEditClinicalRoute(patientId: String) = "patients/$patientId/editClinical"
private fun patientIntakeRoute(patientId: String) = "patients/$patientId/intake"
private fun appointmentDetailRoute(appointmentId: String) = "calendar/appointments/$appointmentId"
private fun chartingListRoute(patientId: String) = "patients/$patientId/chartings"
private fun chartingDetailRoute(chartingId: String) = "chartings/$chartingId"
private fun treatmentPlanListRoute(patientId: String) = "patients/$patientId/treatmentPlans"
private fun treatmentPlanBuilderRoute(patientId: String) = "patients/$patientId/treatmentPlans/new"
private fun treatmentPlanApprovalRoute(patientId: String, planId: String) = "patients/$patientId/treatmentPlans/$planId"
private fun imagingGalleryRoute(patientId: String) = "patients/$patientId/imaging"
private fun imagingUploadRoute(patientId: String) = "patients/$patientId/imaging/add"
private fun imagingCompareRoute(patientId: String, imageIdA: String, imageIdB: String) =
    "patients/$patientId/imaging/compare/$imageIdA/$imageIdB"

/** Everything reachable once the signed-in user has a resolved clinic role. */
@Composable
fun AppNavHost(session: SessionState.Staff, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val clinicId = session.clinicId
    val uid = session.uid

    NavHost(navController = navController, startDestination = ROUTE_DASHBOARD) {
        composable(ROUTE_DASHBOARD) {
            when (session.role) {
                Role.OWNER -> OwnerDashboardScreen(
                    onSignOut = onSignOut,
                    onOpenStaff = { navController.navigate(ROUTE_INVITE_STAFF) },
                    onOpenPatients = { navController.navigate(ROUTE_PATIENT_LIST) },
                    onOpenDataRequests = { navController.navigate(ROUTE_DATA_REQUESTS) },
                    onOpenSchedule = { navController.navigate(ROUTE_CALENDAR) },
                )
                Role.RECEPTIONIST -> ReceptionistDashboardScreen(
                    onSignOut = onSignOut,
                    onOpenPatients = { navController.navigate(ROUTE_PATIENT_LIST) },
                    onOpenSchedule = { navController.navigate(ROUTE_CALENDAR) },
                )
                Role.ASSISTANT -> AssistantDashboardScreen(
                    onSignOut = onSignOut,
                    onOpenPatients = { navController.navigate(ROUTE_PATIENT_LIST) },
                    onOpenSchedule = { navController.navigate(ROUTE_CALENDAR) },
                )
                Role.LAB_COORDINATOR -> LabCoordinatorDashboardScreen(onSignOut = onSignOut)
            }
        }
        composable(ROUTE_INVITE_STAFF) {
            InviteStaffScreen(onDone = { navController.popBackStack() })
        }
        composable(ROUTE_PATIENT_LIST) {
            PatientListScreen(
                clinicId = clinicId,
                onOpenPatient = { patientId -> navController.navigate(patientDetailRoute(patientId)) },
                onAddPatient = { navController.navigate(ROUTE_PATIENT_ADD) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_PATIENT_ADD) {
            PatientFormScreen(
                clinicId = clinicId,
                uid = uid,
                existingPatient = null,
                onSaved = { patientId ->
                    navController.navigate(patientDetailRoute(patientId)) {
                        popUpTo(ROUTE_PATIENT_LIST)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_PATIENT_DETAIL,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty()
            PatientDetailScreen(
                clinicId = clinicId,
                patientId = patientId,
                role = session.role,
                onBack = { navController.popBackStack() },
                onEditDemographics = { navController.navigate(patientEditDemographicsRoute(patientId)) },
                onEditClinicalDetails = { navController.navigate(patientEditClinicalRoute(patientId)) },
                onStartIntake = { navController.navigate(patientIntakeRoute(patientId)) },
                onOpenChartings = { navController.navigate(chartingListRoute(patientId)) },
                onOpenTreatmentPlans = { navController.navigate(treatmentPlanListRoute(patientId)) },
                onOpenImaging = { navController.navigate(imagingGalleryRoute(patientId)) },
            )
        }
        composable(
            ROUTE_PATIENT_EDIT_DEMOGRAPHICS,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) {
            // The demographics form re-fetches the patient itself; passing just the ID
            // keeps this route simple and avoids threading a stale Patient through nav args.
            EditDemographicsRoute(
                clinicId = clinicId,
                uid = uid,
                patientId = it.arguments?.getString(ARG_PATIENT_ID).orEmpty(),
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_PATIENT_EDIT_CLINICAL,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            PatientClinicalEditScreen(
                clinicId = clinicId,
                uid = uid,
                patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty(),
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_PATIENT_INTAKE,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            IntakeFlowScreen(
                clinicId = clinicId,
                uid = uid,
                patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty(),
                onComplete = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_DATA_REQUESTS) {
            DataRequestsScreen(clinicId = clinicId, uid = uid, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_CALENDAR) {
            CalendarScreen(
                clinicId = clinicId,
                role = session.role,
                onBack = { navController.popBackStack() },
                onOpenAppointment = { appointmentId -> navController.navigate(appointmentDetailRoute(appointmentId)) },
                onAddWalkIn = { navController.navigate(ROUTE_WALK_IN) },
                onOpenWaitlist = { navController.navigate(ROUTE_WAITLIST) },
            )
        }
        composable(ROUTE_WALK_IN) {
            WalkInScreen(
                clinicId = clinicId,
                uid = uid,
                onGoToPatientCreation = { navController.navigate(ROUTE_WALK_IN_PATIENT_ADD) },
                onBooked = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_WALK_IN_PATIENT_ADD) {
            // A walk-in-specific copy of the add-patient route: ROUTE_PATIENT_LIST isn't
            // on this branch's back stack (Dashboard -> Calendar -> WalkIn -> here), so the
            // shared route's popUpTo(ROUTE_PATIENT_LIST) can't reach it and would leave the
            // submitted form sitting behind the new patient's detail page. Popping straight
            // back to the calendar instead avoids that stale-form/duplicate-creation trap -
            // the receptionist re-opens the FAB to finish booking now that the patient exists.
            PatientFormScreen(
                clinicId = clinicId,
                uid = uid,
                existingPatient = null,
                onSaved = { navController.popBackStack(ROUTE_CALENDAR, inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_WAITLIST) {
            WaitlistScreen(clinicId = clinicId, uid = uid, onBack = { navController.popBackStack() })
        }
        composable(
            ROUTE_APPOINTMENT_DETAIL,
            arguments = listOf(navArgument(ARG_APPOINTMENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            AppointmentDetailScreen(
                clinicId = clinicId,
                appointmentId = backStackEntry.arguments?.getString(ARG_APPOINTMENT_ID).orEmpty(),
                role = session.role,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_CHARTING_LIST,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty()
            ChartingListScreen(
                clinicId = clinicId,
                uid = uid,
                role = session.role,
                patientId = patientId,
                onOpenCharting = { chartingId -> navController.navigate(chartingDetailRoute(chartingId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_CHARTING_DETAIL,
            arguments = listOf(navArgument(ARG_CHARTING_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            ChartingDetailScreen(
                clinicId = clinicId,
                uid = uid,
                role = session.role,
                chartingId = backStackEntry.arguments?.getString(ARG_CHARTING_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_TREATMENT_PLAN_LIST,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty()
            TreatmentPlanListScreen(
                clinicId = clinicId,
                role = session.role,
                patientId = patientId,
                onOpenPlan = { planId -> navController.navigate(treatmentPlanApprovalRoute(patientId, planId)) },
                onAddPlan = { navController.navigate(treatmentPlanBuilderRoute(patientId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_TREATMENT_PLAN_BUILDER,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty()
            TreatmentPlanBuilderScreen(
                clinicId = clinicId,
                uid = uid,
                patientId = patientId,
                onSaved = { planId ->
                    navController.navigate(treatmentPlanApprovalRoute(patientId, planId)) {
                        popUpTo(treatmentPlanListRoute(patientId))
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_TREATMENT_PLAN_APPROVAL,
            arguments = listOf(
                navArgument(ARG_PATIENT_ID) { type = NavType.StringType },
                navArgument(ARG_PLAN_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty()
            val planId = backStackEntry.arguments?.getString(ARG_PLAN_ID).orEmpty()
            TreatmentPlanApprovalScreen(
                clinicId = clinicId,
                patientId = patientId,
                planId = planId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_IMAGING_GALLERY,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty()
            ImageGalleryScreen(
                clinicId = clinicId,
                role = session.role,
                patientId = patientId,
                onAddImage = { navController.navigate(imagingUploadRoute(patientId)) },
                onCompare = { before, after ->
                    navController.navigate(imagingCompareRoute(patientId, before.id, after.id))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_IMAGING_UPLOAD,
            arguments = listOf(navArgument(ARG_PATIENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            ImageUploadScreen(
                clinicId = clinicId,
                uid = uid,
                patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty(),
                onUploaded = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_IMAGING_COMPARE,
            arguments = listOf(
                navArgument(ARG_PATIENT_ID) { type = NavType.StringType },
                navArgument(ARG_IMAGE_ID_A) { type = NavType.StringType },
                navArgument(ARG_IMAGE_ID_B) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            ImageComparisonRoute(
                clinicId = clinicId,
                patientId = backStackEntry.arguments?.getString(ARG_PATIENT_ID).orEmpty(),
                imageIdA = backStackEntry.arguments?.getString(ARG_IMAGE_ID_A).orEmpty(),
                imageIdB = backStackEntry.arguments?.getString(ARG_IMAGE_ID_B).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Fetches the patient by ID and hands it to [PatientFormScreen] in edit mode. */
@Composable
private fun EditDemographicsRoute(
    clinicId: String,
    uid: String,
    patientId: String,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val patient by ServiceLocator.patientRepository
        .observePatient(clinicId, patientId)
        .collectAsState(initial = null)
    val current = patient

    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PatientFormScreen(
            clinicId = clinicId,
            uid = uid,
            existingPatient = current,
            onSaved = onSaved,
            onBack = onBack,
        )
    }
}

/**
 * Comparison nav args carry two image IDs, not the [com.agastyaone.crmai.data.imaging.ImagingRecord]s
 * themselves (nav args are string-only) - re-observes the patient's imaging list and picks
 * the two matching entries out of it, same "re-fetch by ID from the route" shape as
 * [EditDemographicsRoute] above.
 */
@Composable
private fun ImageComparisonRoute(
    clinicId: String,
    patientId: String,
    imageIdA: String,
    imageIdB: String,
    onBack: () -> Unit,
) {
    val images by ServiceLocator.imagingRepository
        .observeImagingForPatient(clinicId, patientId)
        .collectAsState(initial = emptyList())
    val before = images.firstOrNull { it.id == imageIdA }
    val after = images.firstOrNull { it.id == imageIdB }

    if (before == null || after == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ImageComparisonScreen(before = before, after = after, onBack = onBack)
    }
}
