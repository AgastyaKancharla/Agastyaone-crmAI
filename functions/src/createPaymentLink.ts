import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { REGION } from "./constants";
import { writeAuditLog } from "./audit";
import { RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET } from "./razorpaySecrets";
import { createRazorpayPaymentLink } from "./razorpayClient";

interface CreatePaymentLinkRequest {
  invoiceId: string;
}

/** Just the fields the handler actually reads - not the full Firebase AuthData/DecodedIdToken shape. */
export interface CreatePaymentLinkAuth {
  uid: string;
  role?: unknown;
  clinicId?: unknown;
}

export interface CreatePaymentLinkResult {
  paymentLinkUrl: string;
  razorpayPaymentLinkId: string;
}

/**
 * The actual logic, factored out of the onCall wrapper below so tests can call it
 * directly against the real Firestore emulator (no Functions/Auth emulator needed -
 * constructing a full CallableRequest/DecodedIdToken would be unnecessary ceremony
 * for a function that only ever reads uid/role/clinicId from it).
 *
 * clinicId comes only from `callerAuth.clinicId` (the caller's own custom claim,
 * set by createPaymentLink's onCall wrapper from request.auth.token - never a
 * client-supplied argument), so this can't be used to touch another clinic's
 * invoice just by passing a different clinicId: there is no clinicId argument at
 * all, matching the pattern already established by inviteStaff/updateStaffRole.
 */
export async function createPaymentLinkHandler(
  callerAuth: CreatePaymentLinkAuth,
  data: CreatePaymentLinkRequest,
  keyId: string,
  keySecret: string
): Promise<CreatePaymentLinkResult> {
  const { role: callerRole, clinicId } = callerAuth;
  if ((callerRole !== "owner" && callerRole !== "receptionist") || typeof clinicId !== "string") {
    throw new HttpsError(
      "permission-denied",
      "Only the clinic's owner or receptionist can generate a payment link."
    );
  }

  const invoiceId = typeof data.invoiceId === "string" ? data.invoiceId.trim() : "";
  if (!invoiceId) {
    throw new HttpsError("invalid-argument", "invoiceId is required.");
  }

  const invoiceRef = db.collection("tenants").doc(clinicId).collection("invoices").doc(invoiceId);
  const invoiceSnap = await invoiceRef.get();
  if (!invoiceSnap.exists) {
    // Deliberately the same error whether the invoice doesn't exist at all or
    // belongs to a different clinic - this scoped-by-construction lookup can never
    // find another tenant's invoice, so there's no distinction to leak.
    throw new HttpsError("not-found", "Invoice not found for this clinic.");
  }
  const invoice = invoiceSnap.data() ?? {};

  const total = typeof invoice.total === "number" ? invoice.total : 0;
  const amountPaid = typeof invoice.amountPaid === "number" ? invoice.amountPaid : 0;
  const amountDue = Math.round((total - amountPaid) * 100) / 100;
  if (amountDue <= 0) {
    throw new HttpsError("failed-precondition", "This invoice is already fully paid.");
  }

  const patientId = typeof invoice.patientId === "string" ? invoice.patientId : "";
  const patientSnap = patientId
    ? await db.collection("tenants").doc(clinicId).collection("patients").doc(patientId).get()
    : null;
  const patient = patientSnap?.data() ?? {};
  const customerName = typeof patient.name === "string" && patient.name ? patient.name : "Patient";
  const customerContact = typeof patient.phone === "string" ? patient.phone : null;
  const customerEmail = typeof patient.email === "string" ? patient.email : null;

  const invoiceNumber =
    typeof invoice.invoiceNumber === "string" && invoice.invoiceNumber ? invoice.invoiceNumber : invoiceId;

  const link = await createRazorpayPaymentLink({
    keyId,
    keySecret,
    amountPaise: Math.round(amountDue * 100),
    description: `Invoice ${invoiceNumber}`,
    customerName,
    customerContact,
    customerEmail,
    clinicId,
    invoiceId,
  });

  await invoiceRef.update({
    razorpayPaymentLinkId: link.id,
    razorpayStatus: "created",
  });

  await writeAuditLog({
    clinicId,
    actionType: "payment_link_created",
    performedByUid: callerAuth.uid,
    targetCollection: "invoices",
    targetDocId: invoiceId,
    details: { razorpayPaymentLinkId: link.id, amountDue },
  });

  return { paymentLinkUrl: link.short_url, razorpayPaymentLinkId: link.id };
}

/** Owner/receptionist only, same as the rest of billing - see createPaymentLinkHandler's doc comment. */
export const createPaymentLink = onCall(
  { region: REGION, enforceAppCheck: true, secrets: [RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in first.");
    }
    return createPaymentLinkHandler(
      {
        uid: request.auth.uid,
        role: request.auth.token.role,
        clinicId: request.auth.token.clinicId,
      },
      request.data as CreatePaymentLinkRequest,
      RAZORPAY_KEY_ID.value(),
      RAZORPAY_KEY_SECRET.value()
    );
  }
);
