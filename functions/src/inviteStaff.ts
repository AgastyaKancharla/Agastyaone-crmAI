import { HttpsError, onCall } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { REGION, isClinicRole, INVITABLE_ROLES } from "./constants";
import { writeAuditLog } from "./audit";

interface InviteStaffRequest {
  role: string;
  name: string;
  phone?: string | null;
  email?: string | null;
}

/**
 * Only an owner can invite staff. This is enforced here from the caller's own custom
 * claims (never from a client-supplied clinicId), matching what the Firestore rules
 * trust - a client can't invite itself into someone else's tenant by passing a
 * different clinicId, because there is no clinicId argument at all.
 */
export const inviteStaff = onCall(
  { region: REGION, enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in first.");
    }
    const { role: callerRole, clinicId } = request.auth.token;
    if (callerRole !== "owner" || typeof clinicId !== "string") {
      throw new HttpsError("permission-denied", "Only a clinic owner can invite staff.");
    }

    const data = request.data as InviteStaffRequest;
    if (!isClinicRole(data.role) || !INVITABLE_ROLES.includes(data.role)) {
      throw new HttpsError(
        "invalid-argument",
        `role must be one of: ${INVITABLE_ROLES.join(", ")}`
      );
    }
    const name = typeof data.name === "string" ? data.name.trim() : "";
    if (!name) {
      throw new HttpsError("invalid-argument", "name is required.");
    }
    const phone = typeof data.phone === "string" ? data.phone.trim() : null;
    const email = typeof data.email === "string" ? data.email.trim() : null;
    if (!phone && !email) {
      throw new HttpsError("invalid-argument", "phone or email is required.");
    }

    const inviteRef = db.collection("tenants").doc(clinicId).collection("invites").doc();
    await inviteRef.set({
      inviteId: inviteRef.id,
      role: data.role,
      name,
      phone,
      email,
      status: "pending",
      invitedByUid: request.auth.uid,
      invitedAt: FieldValue.serverTimestamp(),
      acceptedAt: null,
      acceptedByUid: null,
    });

    await writeAuditLog({
      clinicId,
      actionType: "staff_invite_sent",
      performedByUid: request.auth.uid,
      targetCollection: "invites",
      targetDocId: inviteRef.id,
      details: { role: data.role, name, phone, email },
    });

    return { inviteId: inviteRef.id };
  }
);
