/**
 * Integration tests for handleRazorpayWebhookEvent, run against the real local
 * Firestore emulator (see firebase.json / the CI job - never a live project). This
 * calls the handler function directly (imported straight from functions/src, no
 * Functions-emulator HTTP round trip needed) with a hand-signed payload, exactly the
 * shape Razorpay would POST.
 *
 * This is this phase's single most important test file: a webhook that can be
 * fooled by an unsigned or wrongly-signed request, or that blindly trusts a claimed
 * amount, is not safe to deploy even in test mode - see the non-negotiable
 * requirements in the Phase 4b spec.
 */
import { createHmac } from "node:crypto";
import { describe, expect, it } from "vitest";
import { db } from "../functions/src/admin";
import { handleRazorpayWebhookEvent } from "../functions/src/razorpayWebhook";

const WEBHOOK_SECRET = "local_emulator_test_webhook_secret_not_real";

function sign(bodyString: string, secret: string = WEBHOOK_SECRET): string {
  return createHmac("sha256", secret).update(bodyString).digest("hex");
}

function paidEventPayload(opts: {
  linkId: string;
  clinicId: string;
  invoiceId: string;
  paymentId: string;
  paidAmountPaise: number;
}) {
  return {
    event: "payment_link.paid",
    payload: {
      payment_link: {
        entity: {
          id: opts.linkId,
          amount: opts.paidAmountPaise,
          status: "paid",
          notes: { clinicId: opts.clinicId, invoiceId: opts.invoiceId },
        },
      },
      payment: {
        entity: {
          id: opts.paymentId,
          amount: opts.paidAmountPaise,
          status: "captured",
        },
      },
    },
  };
}

async function seedInvoice(clinicId: string, invoiceId: string, total: number): Promise<void> {
  await db
    .collection("tenants")
    .doc(clinicId)
    .collection("invoices")
    .doc(invoiceId)
    .set({
      patientId: "patientX",
      invoiceNumber: "INV-TEST",
      total,
      amountPaid: 0,
      paymentStatus: "unpaid",
      razorpayPaymentLinkId: null,
      razorpayStatus: null,
      razorpayPaymentId: null,
    });
}

async function getInvoice(clinicId: string, invoiceId: string) {
  const snap = await db.collection("tenants").doc(clinicId).collection("invoices").doc(invoiceId).get();
  return snap.data();
}

describe("razorpayWebhook - handleRazorpayWebhookEvent", () => {
  it("CORE: a valid signature with a matching amount marks the invoice paid", async () => {
    const clinicId = "webhookClinicA";
    const invoiceId = "invoicePaid1";
    await seedInvoice(clinicId, invoiceId, 1770);

    const payload = paidEventPayload({
      linkId: "plink_1",
      clinicId,
      invoiceId,
      paymentId: "pay_1",
      paidAmountPaise: 177000,
    });
    const bodyString = JSON.stringify(payload);

    const result = await handleRazorpayWebhookEvent(
      Buffer.from(bodyString, "utf8"),
      sign(bodyString),
      payload,
      WEBHOOK_SECRET
    );

    expect(result.status).toBe(200);
    const invoice = await getInvoice(clinicId, invoiceId);
    expect(invoice?.paymentStatus).toBe("paid");
    expect(invoice?.amountPaid).toBe(1770);
    expect(invoice?.razorpayStatus).toBe("paid");
    expect(invoice?.razorpayPaymentId).toBe("pay_1");
  });

  it("CORE: an invalid or missing signature is rejected and updates nothing", async () => {
    const clinicId = "webhookClinicA";
    const invoiceId = "invoicePaid2";
    await seedInvoice(clinicId, invoiceId, 1770);

    const payload = paidEventPayload({
      linkId: "plink_2",
      clinicId,
      invoiceId,
      paymentId: "pay_2",
      paidAmountPaise: 177000,
    });
    const bodyString = JSON.stringify(payload);
    const rawBody = Buffer.from(bodyString, "utf8");

    const wrongSignature = await handleRazorpayWebhookEvent(
      rawBody,
      sign(bodyString, "totally-wrong-secret"),
      payload,
      WEBHOOK_SECRET
    );
    expect(wrongSignature.status).toBe(400);

    const missingSignature = await handleRazorpayWebhookEvent(rawBody, undefined, payload, WEBHOOK_SECRET);
    expect(missingSignature.status).toBe(400);

    const invoice = await getInvoice(clinicId, invoiceId);
    expect(invoice?.paymentStatus).toBe("unpaid");
    expect(invoice?.amountPaid).toBe(0);
    expect(invoice?.razorpayPaymentId).toBeNull();
  });

  it("CORE: an amount that doesn't match what's owed is flagged, not applied", async () => {
    const clinicId = "webhookClinicA";
    const invoiceId = "invoiceMismatch1";
    await seedInvoice(clinicId, invoiceId, 1770);

    const payload = paidEventPayload({
      linkId: "plink_3",
      clinicId,
      invoiceId,
      paymentId: "pay_3",
      paidAmountPaise: 999999, // far more than the 177000 paise actually due
    });
    const bodyString = JSON.stringify(payload);

    const result = await handleRazorpayWebhookEvent(
      Buffer.from(bodyString, "utf8"),
      sign(bodyString),
      payload,
      WEBHOOK_SECRET
    );

    expect(result.status).toBe(200);
    expect(result.body.flagged).toBe("amount_mismatch");
    const invoice = await getInvoice(clinicId, invoiceId);
    expect(invoice?.paymentStatus).toBe("unpaid");
    expect(invoice?.amountPaid).toBe(0);
  });

  it("CORE: the same valid event delivered twice does not double-apply the payment", async () => {
    const clinicId = "webhookClinicA";
    const invoiceId = "invoiceDup1";
    await seedInvoice(clinicId, invoiceId, 1770);

    const payload = paidEventPayload({
      linkId: "plink_4",
      clinicId,
      invoiceId,
      paymentId: "pay_4",
      paidAmountPaise: 177000,
    });
    const bodyString = JSON.stringify(payload);
    const signature = sign(bodyString);
    const rawBody = Buffer.from(bodyString, "utf8");

    const first = await handleRazorpayWebhookEvent(rawBody, signature, payload, WEBHOOK_SECRET);
    expect(first.status).toBe(200);

    const second = await handleRazorpayWebhookEvent(rawBody, signature, payload, WEBHOOK_SECRET);
    expect(second.status).toBe(200);
    expect(second.body.duplicate).toBe(true);

    const invoice = await getInvoice(clinicId, invoiceId);
    expect(invoice?.amountPaid).toBe(1770);
    expect(invoice?.paymentStatus).toBe("paid");
  });

  it("updates razorpayStatus (not paymentStatus) for expired/cancelled links", async () => {
    const clinicId = "webhookClinicA";
    const invoiceId = "invoiceExpired1";
    await seedInvoice(clinicId, invoiceId, 1770);

    const expiredPayload = {
      event: "payment_link.expired",
      payload: {
        payment_link: {
          entity: { id: "plink_5", amount: 177000, status: "expired", notes: { clinicId, invoiceId } },
        },
      },
    };
    const bodyString = JSON.stringify(expiredPayload);
    const result = await handleRazorpayWebhookEvent(
      Buffer.from(bodyString, "utf8"),
      sign(bodyString),
      expiredPayload,
      WEBHOOK_SECRET
    );

    expect(result.status).toBe(200);
    const invoice = await getInvoice(clinicId, invoiceId);
    expect(invoice?.razorpayStatus).toBe("expired");
    expect(invoice?.paymentStatus).toBe("unpaid");
  });
});
