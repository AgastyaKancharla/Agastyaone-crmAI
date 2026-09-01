import { HttpsError, onCall } from "firebase-functions/v2/https";
import { auth, db } from "./admin";
import { REGION, isClinicRole, INVITABLE_ROLES } from "./constants";
import { writeAuditLog } from "./audit";

interface UpdateStaffRoleRequest {
  targetUid: string;
  newRole: string;
}

/** Owner-only. Changes both the staff doc and the custom claim together, so they never drift. */
export const updateStaffRole = onCall(
  { region: REGION, enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in first.");
    }
    const { role: callerRole, clinicId } = request.auth.token;
    if (callerRole !== "owner" || typeof clinicId !== "string") {
      throw new HttpsError("permission-denied", "Only a clinic owner can change staff roles.");
    }

    const data = request.data as UpdateStaffRoleRequest;
    const targetUid = typeof data.targetUid === "string" ? data.targetUid.trim() : "";
    if (!targetUid) {
      throw new HttpsError("invalid-argument", "targetUid is required.");
    }
    if (targetUid === request.auth.uid) {
      throw new HttpsError("invalid-argument", "Owners cannot change their own role here.");
    }
    if (!isClinicRole(data.newRole) || !INVITABLE_ROLES.includes(data.newRole)) {
      throw new HttpsError(
        "invalid-argument",
        `newRole must be one of: ${INVITABLE_ROLES.join(", ")}`
      );
    }

    const staffRef = db.collection("tenants").doc(clinicId).collection("staff").doc(targetUid);
    const staffSnap = await staffRef.get();
    if (!staffSnap.exists) {
      throw new HttpsError("not-found", "That staff member does not belong to this clinic.");
    }
    const previousRole = staffSnap.data()?.role;

    await staffRef.update({ role: data.newRole });
    await auth.setCustomUserClaims(targetUid, { role: data.newRole, clinicId });

    await writeAuditLog({
      clinicId,
      actionType: "role_change",
      performedByUid: request.auth.uid,
      targetCollection: "staff",
      targetDocId: targetUid,
      details: { previousRole, newRole: data.newRole },
    });

    return { ok: true };
  }
);
