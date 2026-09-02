import { onRequest } from "firebase-functions/v2/https";
import { db } from "./admin";
import { REGION } from "./constants";
import { writeAuditLog } from "./audit";
import { RAZORPAY_WEBHOOK_SECRET } from "./razorpaySecrets";
import { verifyRazorpaySignature } from "./razorpayClient";

interface RazorpayWebhookEvent {
  event?: string;
  payload?: {
    payment_link?: {
      entity?: {
        id?: string;
        amount?: number;
        status?: string;
        notes?: { clinicId?: string; invoiceId?: string };
      };
    };
    payment?: {
      entity?: {
        id?: string;
        amount?: number;
        status?: string;
      };
    };
  };
}

export interface WebhookResult {
  status: number;
  body: Record<string, unknown>;
}

/**
 * The actual logic, factored out of the onRequest wrapper below so tests can call it
 * directly against the real Firestore emulator without needing the Functions
 * emulator's HTTP layer.
 *
 * This is a server-to-server call from Razorpay, using the Admin SDK - it bypasses
 * firestore.rules entirely (rules only govern client access), so signature
 * verification is the *only* thing standing between the internet and marking an
 * invoice paid. Nothing here reads or writes Firestore until the signature check
 * passes - that check must be the very first thing this function does, on every path.
 */
export async function handleRazorpayWebhookEvent(
  rawBody: Buffer,
  signatureHeader: string | undefined,
  parsedBody: unknown,
  webhookSecret: string
): Promise<WebhookResult> {
  if (!verifyRazorpaySignature(rawBody, signatureHeader, webhookSecret)) {
    return { status: 400, body: { error: "Invalid signature" } };
  }

  const event = parsedBody as RazorpayWebhookEvent;
  const eventType = event?.event;
  const notes = event?.payload?.payment_link?.entity?.notes;
  const clinicId = notes?.clinicId;
  const invoiceId = notes?.invoiceId;

  if (!clinicId || !invoiceId) {
    return { status: 400, body: { error: "Missing clinicId/invoiceId in payment link notes" } };
  }

  const invoiceRef = db.collection("tenants").doc(clinicId).collection("invoices").doc(invoiceId);

  if (eventType === "payment_link.expired") {
    await invoiceRef.update({ razorpayStatus: "expired" });
    return { status: 200, body: { ok: true } };
  }
  if (eventType === "payment_link.cancelled") {
    await invoiceRef.update({ razorpayStatus: "cancelled" });
    return { status: 200, body: { ok: true } };
  }
  if (eventType !== "payment_link.paid") {
    // Not an event this phase acts on - acknowledge so Razorpay stops retrying it.
    return { status: 200, body: { ok: true, ignored: eventType ?? null } };
  }

  const paymentEntity = event.payload?.payment?.entity;
  const paymentId = paymentEntity?.id;
  const paidAmountPaise = paymentEntity?.amount;
  if (!paymentId || typeof paidAmountPaise !== "number") {
    return { status: 400, body: { error: "Malformed payment payload" } };
  }

  const invoiceSnap = await invoiceRef.get();
  if (!invoiceSnap.exists) {
    return { status: 404, body: { error: "Invoice not found" } };
  }
  const invoice = invoiceSnap.data() ?? {};

  // Idempotency: Razorpay retries on any non-2xx response or timeout, so the same
  // "paid" event can arrive more than once. A payment ID we've already recorded
  // against this invoice means skip, not reapply.
  if (invoice.razorpayPaymentId === paymentId) {
    return { status: 200, body: { ok: true, duplicate: true } };
  }

  const total = typeof invoice.total === "number" ? invoice.total : 0;
  const amountPaidSoFar = typeof invoice.amountPaid === "number" ? invoice.amountPaid : 0;
  const amountDuePaise = Math.round((total - amountPaidSoFar) * 100);

  if (paidAmountPaise <= 0 || paidAmountPaise > amountDuePaise) {
    // Flagged, never applied - "Razorpay says paid" is not by itself proof the
    // right amount was paid.
    await writeAuditLog({
      clinicId,
      actionType: "payment_amount_mismatch",
      performedByUid: "razorpay-webhook",
      targetCollection: "invoices",
      targetDocId: invoiceId,
      details: { paymentId, paidAmountPaise, amountDuePaise },
    });
    return { status: 200, body: { ok: true, flagged: "amount_mismatch" } };
  }

  const isFullSettlement = paidAmountPaise === amountDuePaise;
  const newAmountPaid = amountPaidSoFar + paidAmountPaise / 100;

  await invoiceRef.update({
    paymentStatus: isFullSettlement ? "paid" : "partial",
    amountPaid: newAmountPaid,
    razorpayStatus: "paid",
    razorpayPaymentId: paymentId,
  });

  await writeAuditLog({
    clinicId,
    actionType: "payment_received",
    performedByUid: "razorpay-webhook",
    targetCollection: "invoices",
    targetDocId: invoiceId,
    details: { paymentId, paidAmountPaise, newAmountPaid },
  });

  return { status: 200, body: { ok: true } };
}

export const razorpayWebhook = onRequest(
  { region: REGION, secrets: [RAZORPAY_WEBHOOK_SECRET] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const result = await handleRazorpayWebhookEvent(
      req.rawBody,
      req.get("x-razorpay-signature"),
      req.body,
      RAZORPAY_WEBHOOK_SECRET.value()
    );
    res.status(result.status).json(result.body);
  }
);
