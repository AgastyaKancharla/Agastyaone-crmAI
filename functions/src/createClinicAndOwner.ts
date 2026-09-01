import { HttpsError, onCall } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { auth, db } from "./admin";
import { REGION } from "./constants";
import { writeAuditLog } from "./audit";

interface CreateClinicAndOwnerRequest {
  clinicName: string;
  address: string;
  city: string;
  state: string;
  gstin?: string | null;
  ownerName: string;
  ownerPhone?: string | null;
  ownerEmail?: string | null;
}

function requireNonBlank(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `"${field}" is required.`);
  }
  return value.trim();
}

/**
 * First-time owner signup. Runs entirely server-side (Admin SDK bypasses Firestore
 * rules) so the tenant doc, the owner's own staff doc, and their custom claims are
 * created atomically - a client can never end up with claims but no tenant, or a
 * tenant but no owner staff record.
 */
export const createClinicAndOwner = onCall(
  { region: REGION, enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before creating a clinic.");
    }
    const uid = request.auth.uid;

    if (request.auth.token.clinicId || request.auth.token.role) {
      throw new HttpsError(
        "failed-precondition",
        "This account is already linked to a clinic."
      );
    }

    const data = request.data as CreateClinicAndOwnerRequest;
    const clinicName = requireNonBlank(data.clinicName, "clinicName");
    const address = requireNonBlank(data.address, "address");
    const city = requireNonBlank(data.city, "city");
    const state = requireNonBlank(data.state, "state");
    const ownerName = requireNonBlank(data.ownerName, "ownerName");
    const gstin = typeof data.gstin === "string" ? data.gstin.trim() : null;
    const ownerPhone = typeof data.ownerPhone === "string" ? data.ownerPhone.trim() : null;
    const ownerEmail = typeof data.ownerEmail === "string" ? data.ownerEmail.trim() : null;

    if (!ownerPhone && !ownerEmail) {
      throw new HttpsError("invalid-argument", "ownerPhone or ownerEmail is required.");
    }

    const clinicRef = db.collection("tenants").doc();
    const clinicId = clinicRef.id;
    const staffRef = clinicRef.collection("staff").doc(uid);

    await db.runTransaction(async (transaction) => {
      transaction.set(clinicRef, {
        clinicName,
        address,
        city,
        state,
        gstin,
        subscriptionStatus: "trial",
        createdAt: FieldValue.serverTimestamp(),
        ownerUid: uid,
      });
      transaction.set(staffRef, {
        name: ownerName,
        role: "owner",
        phone: ownerPhone,
        email: ownerEmail,
        active: true,
        invitedAt: null,
        joinedAt: FieldValue.serverTimestamp(),
        invitedByUid: null,
      });
    });

    await auth.setCustomUserClaims(uid, { role: "owner", clinicId });

    await writeAuditLog({
      clinicId,
      actionType: "clinic_created",
      performedByUid: uid,
      targetCollection: "tenants",
      targetDocId: clinicId,
      details: { clinicName },
    });

    return { clinicId };
  }
);
