import { createHmac, timingSafeEqual } from "node:crypto";

/**
 * Recomputes Razorpay's webhook HMAC-SHA256 signature over the exact raw request
 * bytes and compares it to the `X-Razorpay-Signature` header with a constant-time
 * comparison (timingSafeEqual) so response-time differences can't leak how much of
 * the signature matched. This is the *only* protection on the webhook endpoint -
 * Firestore rules don't apply to the Admin SDK, so a wrong or missing signature must
 * never let a request anywhere near a Firestore read or write.
 */
export function verifyRazorpaySignature(
  rawBody: Buffer,
  signatureHeader: string | undefined,
  secret: string
): boolean {
  if (!signatureHeader) {
    return false;
  }
  const expected = createHmac("sha256", secret).update(rawBody).digest("hex");
  const expectedBuf = Buffer.from(expected, "utf8");
  const providedBuf = Buffer.from(signatureHeader, "utf8");
  if (expectedBuf.length !== providedBuf.length) {
    return false;
  }
  return timingSafeEqual(expectedBuf, providedBuf);
}

export interface RazorpayPaymentLinkResponse {
  id: string;
  short_url: string;
  status: string;
}

export interface CreateRazorpayPaymentLinkParams {
  keyId: string;
  keySecret: string;
  amountPaise: number;
  description: string;
  customerName: string;
  customerContact?: string | null;
  customerEmail?: string | null;
  clinicId: string;
  invoiceId: string;
  /** Injectable only for tests - production always uses the real global fetch. */
  fetchImpl?: typeof fetch;
}

/**
 * Calls Razorpay's Payment Links API server-side with the key secret (never sent to
 * or stored on the client). `notes` carries our own clinicId/invoiceId so the webhook
 * can look up the exact Firestore document directly, with no cross-tenant query
 * needed. `accept_partial` is deliberately left off (full-amount-only links) to keep
 * this phase's amount-verification logic simple - a partial-payment-via-link mode is
 * not required by the spec.
 */
export async function createRazorpayPaymentLink(
  params: CreateRazorpayPaymentLinkParams
): Promise<RazorpayPaymentLinkResponse> {
  const fetchFn = params.fetchImpl ?? fetch;
  const authHeader = Buffer.from(`${params.keyId}:${params.keySecret}`).toString("base64");

  const response = await fetchFn("https://api.razorpay.com/v1/payment_links", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Basic ${authHeader}`,
    },
    body: JSON.stringify({
      amount: params.amountPaise,
      currency: "INR",
      description: params.description,
      customer: {
        name: params.customerName,
        contact: params.customerContact ?? undefined,
        email: params.customerEmail ?? undefined,
      },
      notify: {
        sms: Boolean(params.customerContact),
        email: Boolean(params.customerEmail),
      },
      notes: {
        clinicId: params.clinicId,
        invoiceId: params.invoiceId,
      },
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Razorpay payment link creation failed (${response.status}): ${body}`);
  }

  return (await response.json()) as RazorpayPaymentLinkResponse;
}
