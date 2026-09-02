/**
 * Integration tests for createPaymentLinkHandler, run against the real local
 * Firestore emulator. The one real network call this function makes (to Razorpay's
 * Payment Links API) is stubbed via a fake global fetch for the success case only -
 * the authorization-rejection cases never reach the network call at all, which the
 * tests confirm by leaving fetch unstubbed for them.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { db } from "../functions/src/admin";
import { createPaymentLinkHandler } from "../functions/src/createPaymentLink";

const TEST_KEY_ID = "local_emulator_test_key_id_not_real";
const TEST_KEY_SECRET = "local_emulator_test_key_secret_not_real";

/**
 * functions/ and functions-tests/ are separate npm installs, so an HttpsError
 * thrown by code in functions/src is a different class object than any HttpsError
 * imported directly here (a classic dual-package "instanceof" hazard) - asserting
 * on the FunctionsErrorCode string it carries sidesteps that entirely, and is more
 * precise than a bare instanceof check would have been anyway.
 */
async function expectHttpsErrorCode(promise: Promise<unknown>, expectedCode: string): Promise<void> {
  await expect(promise).rejects.toMatchObject({ code: expectedCode });
}

function stubSuccessfulRazorpayFetch(): void {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ id: "plink_test123", short_url: "https://rzp.io/i/testlink", status: "created" }),
      text: async () => "",
    }))
  );
}

async function seedFixtures(): Promise<void> {
  await db.collection("tenants").doc("clinicA").set({ clinicName: "Clinic A", state: "Karnataka" });
  await db.collection("tenants").doc("clinicA").collection("patients").doc("patientA1").set({
    name: "Alice Apple",
    phone: "+911234500001",
    email: null,
  });
  await db.collection("tenants").doc("clinicA").collection("invoices").doc("invoiceA1").set({
    patientId: "patientA1",
    invoiceNumber: "INV-0001",
    total: 1770,
    amountPaid: 0,
    paymentStatus: "unpaid",
    razorpayPaymentLinkId: null,
    razorpayStatus: null,
    razorpayPaymentId: null,
  });
  await db.collection("tenants").doc("clinicA").collection("invoices").doc("invoicePaidUp").set({
    patientId: "patientA1",
    invoiceNumber: "INV-0002",
    total: 500,
    amountPaid: 500,
    paymentStatus: "paid",
    razorpayPaymentLinkId: null,
    razorpayStatus: null,
    razorpayPaymentId: null,
  });
  await db.collection("tenants").doc("clinicB").set({ clinicName: "Clinic B", state: "Tamil Nadu" });
  await db.collection("tenants").doc("clinicB").collection("invoices").doc("invoiceB1").set({
    patientId: "patientB1",
    invoiceNumber: "INV-0001",
    total: 1000,
    amountPaid: 0,
    paymentStatus: "unpaid",
    razorpayPaymentLinkId: null,
    razorpayStatus: null,
    razorpayPaymentId: null,
  });
}

describe("createPaymentLink - createPaymentLinkHandler", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("CORE: a receptionist can generate a payment link for their own clinic's invoice", async () => {
    await seedFixtures();
    stubSuccessfulRazorpayFetch();

    const result = await createPaymentLinkHandler(
      { uid: "receptionistA", role: "receptionist", clinicId: "clinicA" },
      { invoiceId: "invoiceA1" },
      TEST_KEY_ID,
      TEST_KEY_SECRET
    );

    expect(result.paymentLinkUrl).toBe("https://rzp.io/i/testlink");
    expect(result.razorpayPaymentLinkId).toBe("plink_test123");

    const invoiceSnap = await db.collection("tenants").doc("clinicA").collection("invoices").doc("invoiceA1").get();
    expect(invoiceSnap.data()?.razorpayPaymentLinkId).toBe("plink_test123");
    expect(invoiceSnap.data()?.razorpayStatus).toBe("created");
  });

  it("an owner can also generate a payment link", async () => {
    await seedFixtures();
    stubSuccessfulRazorpayFetch();

    const result = await createPaymentLinkHandler(
      { uid: "ownerA", role: "owner", clinicId: "clinicA" },
      { invoiceId: "invoiceA1" },
      TEST_KEY_ID,
      TEST_KEY_SECRET
    );

    expect(result.razorpayPaymentLinkId).toBe("plink_test123");
  });

  it("CORE: an assistant is rejected outright, even for their own clinic's invoice", async () => {
    await seedFixtures();

    await expectHttpsErrorCode(
      createPaymentLinkHandler(
        { uid: "assistantA", role: "assistant", clinicId: "clinicA" },
        { invoiceId: "invoiceA1" },
        TEST_KEY_ID,
        TEST_KEY_SECRET
      ),
      "permission-denied"
    );

    const invoiceSnap = await db.collection("tenants").doc("clinicA").collection("invoices").doc("invoiceA1").get();
    expect(invoiceSnap.data()?.razorpayPaymentLinkId).toBeNull();
  });

  it("CORE: a receptionist cannot generate a payment link for another clinic's invoice", async () => {
    await seedFixtures();

    // clinicId always comes from the caller's own custom claim (clinicA here), never
    // a client-supplied argument - so this looks up "invoiceB1" under clinicA
    // (which doesn't exist) rather than ever reaching clinic B's real invoice.
    await expectHttpsErrorCode(
      createPaymentLinkHandler(
        { uid: "receptionistA", role: "receptionist", clinicId: "clinicA" },
        { invoiceId: "invoiceB1" },
        TEST_KEY_ID,
        TEST_KEY_SECRET
      ),
      "not-found"
    );

    const invoiceSnap = await db.collection("tenants").doc("clinicB").collection("invoices").doc("invoiceB1").get();
    expect(invoiceSnap.data()?.razorpayPaymentLinkId).toBeNull();
  });

  it("rejects generating a link for an invoice that's already fully paid", async () => {
    await seedFixtures();

    await expectHttpsErrorCode(
      createPaymentLinkHandler(
        { uid: "receptionistA", role: "receptionist", clinicId: "clinicA" },
        { invoiceId: "invoicePaidUp" },
        TEST_KEY_ID,
        TEST_KEY_SECRET
      ),
      "failed-precondition"
    );
  });
});
