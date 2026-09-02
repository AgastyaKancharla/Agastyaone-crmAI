/**
 * Rules-unit tests for firestore.rules, run against the Firestore emulator
 * (see firebase.json / the CI "rules-test" job - never a live project).
 *
 * These test what the Phase 1 spec says the rules should do, not just what
 * the current rules happen to do: a failing test here means the rules are
 * wrong, not the test.
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  type RulesTestEnvironment,
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, afterEach, beforeAll, describe, it } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ID = "demo-agastyaone-rules-test";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearFirestore();
});

/** Seeds fixture data with the Admin SDK's privileges, bypassing the rules under test. */
async function seedFixtures(): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "tenants/A"), { clinicName: "Clinic A", ownerUid: "ownerA" });
    await setDoc(doc(db, "tenants/A/staff/ownerA"), { role: "owner", name: "Owner A" });
    await setDoc(doc(db, "tenants/A/staff/receptionistA"), {
      role: "receptionist",
      name: "Receptionist A",
    });
    await setDoc(doc(db, "tenants/A/staff/assistantA"), {
      role: "assistant",
      name: "Assistant A",
    });
    await setDoc(doc(db, "tenants/A/staff/labCoordinatorA"), {
      role: "labCoordinator",
      name: "Lab Coordinator A",
    });
    await setDoc(doc(db, "tenants/A/patients/patientA1"), {
      name: "Alice Apple",
      phone: "+911234500001",
      address: "1 Main St, Bengaluru",
      allergies: [],
      medicalHistoryNotes: "",
      createdByUid: "receptionistA",
    });
    await setDoc(doc(db, "tenants/A/patients/patientA1/consents/whatsappMarketing"), {
      consentType: "whatsappMarketing",
      granted: true,
      grantedAt: "2026-01-01T00:00:00Z",
      revokedAt: null,
      signatureUrl: null,
      recordedByUid: "receptionistA",
    });
    await setDoc(doc(db, "tenants/B"), { clinicName: "Clinic B", ownerUid: "ownerB" });
    await setDoc(doc(db, "tenants/B/staff/ownerB"), { role: "owner", name: "Owner B" });
    await setDoc(doc(db, "tenants/B/patients/patientB1"), {
      name: "Bob Banana",
      phone: "+911234500002",
      address: "1 Main St, Chennai",
      allergies: [],
      medicalHistoryNotes: "",
      createdByUid: "ownerB",
    });
    await setDoc(doc(db, "tenants/B/patients/patientB1/consents/whatsappMarketing"), {
      consentType: "whatsappMarketing",
      granted: false,
      grantedAt: null,
      revokedAt: null,
      signatureUrl: null,
      recordedByUid: "ownerB",
    });
    await setDoc(doc(db, "tenants/A/appointments/appointmentA1"), {
      patientId: "patientA1",
      patientName: "Alice Apple",
      dentistUid: "ownerA",
      startTime: "2026-02-01T09:00:00Z",
      endTime: "2026-02-01T09:30:00Z",
      status: "scheduled",
      source: "phone",
      notes: "",
      createdByUid: "receptionistA",
      createdAt: "2026-01-15T00:00:00Z",
      cancelledReason: null,
    });
    await setDoc(doc(db, "tenants/A/waitlist/waitlistA1"), {
      patientId: "patientA1",
      patientName: "Alice Apple",
      preferredDates: ["2026-02-03T00:00:00Z"],
      notes: "",
      addedAt: "2026-01-15T00:00:00Z",
      addedByUid: "receptionistA",
      status: "waiting",
    });
    await setDoc(doc(db, "tenants/B/appointments/appointmentB1"), {
      patientId: "patientB1",
      patientName: "Bob Banana",
      dentistUid: "ownerB",
      startTime: "2026-02-01T10:00:00Z",
      endTime: "2026-02-01T10:30:00Z",
      status: "scheduled",
      source: "phone",
      notes: "",
      createdByUid: "ownerB",
      createdAt: "2026-01-15T00:00:00Z",
      cancelledReason: null,
    });
    await setDoc(doc(db, "tenants/A/chartings/chartingA1"), {
      patientId: "patientA1",
      visitDate: "2026-02-01T00:00:00Z",
      dentistUid: "ownerA",
      dentitionType: "adult",
      toothConditions: {
        "11": { surfaces: { occlusal: "caries" }, notes: "" },
      },
      periodontalChart: {
        "11": {
          pocketDepths: [2, 2, 3, 2, 2, 3],
          bleeding: [false, false, false, false, false, false],
          mobilityGrade: 0,
        },
      },
      lastEditedByUid: "ownerA",
      lastEditedAt: "2026-02-01T00:00:00Z",
    });
    await setDoc(doc(db, "tenants/A/treatmentPlans/planA1"), {
      patientId: "patientA1",
      createdByUid: "ownerA",
      createdAt: "2026-02-01T00:00:00Z",
      status: "draft",
      lineItems: [
        { procedureCode: "SCALING", procedureName: "Scaling & Polishing", toothNumber: null, estimatedCost: 1500, status: "pending" },
      ],
      totalEstimate: 1500,
      patientApprovalSignatureUrl: null,
      patientApprovedAt: null,
    });
    await setDoc(doc(db, "tenants/B/chartings/chartingB1"), {
      patientId: "patientB1",
      visitDate: "2026-02-01T00:00:00Z",
      dentistUid: "ownerB",
      dentitionType: "adult",
      toothConditions: {},
      periodontalChart: {},
      lastEditedByUid: "ownerB",
      lastEditedAt: "2026-02-01T00:00:00Z",
    });
    await setDoc(doc(db, "tenants/B/treatmentPlans/planB1"), {
      patientId: "patientB1",
      createdByUid: "ownerB",
      createdAt: "2026-02-01T00:00:00Z",
      status: "draft",
      lineItems: [],
      totalEstimate: 0,
      patientApprovalSignatureUrl: null,
      patientApprovedAt: null,
    });
    await setDoc(doc(db, "tenants/A/imaging/imagingA1"), {
      patientId: "patientA1",
      toothNumber: "11",
      type: "RVG",
      storageUrl: "https://example.com/imagingA1.jpg",
      capturedAt: "2026-02-01T00:00:00Z",
      uploadedByUid: "assistantA",
      notes: null,
    });
    await setDoc(doc(db, "tenants/B/imaging/imagingB1"), {
      patientId: "patientB1",
      toothNumber: null,
      type: "OPG",
      storageUrl: "https://example.com/imagingB1.jpg",
      capturedAt: "2026-02-01T00:00:00Z",
      uploadedByUid: "ownerB",
      notes: null,
    });
    await setDoc(doc(db, "tenants/A/invoices/invoiceA1"), {
      patientId: "patientA1",
      invoiceNumber: "INV-0001",
      issuedAt: "2026-02-05T00:00:00Z",
      issuedByUid: "receptionistA",
      lineItems: [
        {
          procedureCode: "SCALING",
          procedureName: "Scaling & Polishing",
          hsnSacCode: "999319",
          quantity: 1,
          unitCost: 1500,
          lineTotal: 1500,
        },
      ],
      billingState: "Karnataka",
      subtotal: 1500,
      cgst: 135,
      sgst: 135,
      igst: 0,
      total: 1770,
      paymentStatus: "unpaid",
      amountPaid: 0,
      razorpayPaymentLinkId: null,
      razorpayStatus: null,
      treatmentPlanId: null,
    });
    await setDoc(doc(db, "tenants/B/invoices/invoiceB1"), {
      patientId: "patientB1",
      invoiceNumber: "INV-0001",
      issuedAt: "2026-02-05T00:00:00Z",
      issuedByUid: "ownerB",
      lineItems: [],
      billingState: "Tamil Nadu",
      subtotal: 0,
      cgst: 0,
      sgst: 0,
      igst: 0,
      total: 0,
      paymentStatus: "unpaid",
      amountPaid: 0,
      razorpayPaymentLinkId: null,
      razorpayStatus: null,
      treatmentPlanId: null,
    });
    await setDoc(doc(db, "platformAdmins/adminX"), {
      name: "Admin X",
      email: "adminx@agastyaone.com",
    });
  });
}

function ownerAContext() {
  return testEnv.authenticatedContext("ownerA", { role: "owner", clinicId: "A" });
}

function receptionistAContext() {
  return testEnv.authenticatedContext("receptionistA", { role: "receptionist", clinicId: "A" });
}

function assistantAContext() {
  return testEnv.authenticatedContext("assistantA", { role: "assistant", clinicId: "A" });
}

function labCoordinatorAContext() {
  return testEnv.authenticatedContext("labCoordinatorA", {
    role: "labCoordinator",
    clinicId: "A",
  });
}

function platformAdminContext() {
  return testEnv.authenticatedContext("adminX", { platformAdmin: true });
}

/** Signed in (finished phone/email auth) but the invite/signup function hasn't run yet. */
function noClaimContext() {
  return testEnv.authenticatedContext("newUser");
}

describe("tenant isolation", () => {
  it("lets the owner of clinic A read tenants/A and write a staff doc under it", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertSucceeds(getDoc(doc(db, "tenants/A")));
    await assertSucceeds(
      setDoc(doc(db, "tenants/A/staff/newHireUid"), { role: "assistant", name: "New Hire" })
    );
  });

  it("CORE: denies the owner of clinic A any read or write under tenants/B", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/B")));
    await assertFails(getDoc(doc(db, "tenants/B/staff/ownerB")));
    await assertFails(
      setDoc(doc(db, "tenants/B/staff/intruderUid"), { role: "owner", name: "Intruder" })
    );
  });
});

describe("role-based access within a tenant", () => {
  it("lets a non-owner staff member read the tenant doc and their own staff doc", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertSucceeds(getDoc(doc(db, "tenants/A")));
    await assertSucceeds(getDoc(doc(db, "tenants/A/staff/receptionistA")));
  });

  it("denies a non-owner staff member any write to the tenant doc or a staff doc", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertFails(updateDoc(doc(db, "tenants/A"), { clinicName: "Hacked" }));
    await assertFails(
      setDoc(doc(db, "tenants/A/staff/receptionistA"), { role: "owner" }, { merge: true })
    );
    await assertFails(
      setDoc(doc(db, "tenants/A/staff/anotherHireUid"), {
        role: "assistant",
        name: "Another Hire",
      })
    );
  });

  it("CORE: lets the receptionist bump only the invoiceCounter field on the tenant doc, nothing else", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();
    const tenantRef = doc(db, "tenants/A");

    await assertSucceeds(updateDoc(tenantRef, { invoiceCounter: 1 }));
    await assertFails(updateDoc(tenantRef, { invoiceCounter: 2, clinicName: "Hacked via counter" }));
    await assertFails(updateDoc(tenantRef, { gstin: "FAKEGSTIN" }));
  });
});

describe("platform admin", () => {
  it("can read across multiple tenants", async () => {
    await seedFixtures();
    const db = platformAdminContext().firestore();

    await assertSucceeds(getDoc(doc(db, "tenants/A")));
    await assertSucceeds(getDoc(doc(db, "tenants/B")));
  });

  it("cannot write to any tenant's data - no blanket write access by default", async () => {
    await seedFixtures();
    const db = platformAdminContext().firestore();

    await assertFails(updateDoc(doc(db, "tenants/A"), { clinicName: "Admin edit" }));
    await assertFails(
      setDoc(doc(db, "tenants/A/staff/adminAddedUid"), { role: "assistant", name: "Admin Added" })
    );
  });
});

describe("platformAdmins collection", () => {
  it("lets a platform admin read and write only their own doc", async () => {
    await seedFixtures();
    const db = platformAdminContext().firestore();

    await assertSucceeds(getDoc(doc(db, "platformAdmins/adminX")));
    await assertSucceeds(
      setDoc(doc(db, "platformAdmins/adminX"), { name: "Admin X Updated" }, { merge: true })
    );
  });

  it("denies any other authenticated user read or write access to it", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(getDoc(doc(db, "platformAdmins/adminX")));
    await assertFails(
      setDoc(doc(db, "platformAdmins/adminX"), { name: "Overwritten" }, { merge: true })
    );
  });
});

describe("unauthenticated and mid-onboarding access", () => {
  it("denies any unauthenticated request to a tenant path", async () => {
    await seedFixtures();
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A")));
    await assertFails(
      setDoc(doc(db, "tenants/A/staff/uninvitedUid"), { role: "assistant", name: "Uninvited" })
    );
  });

  it("denies a signed-in user with no clinicId claim yet on all tenant paths", async () => {
    await seedFixtures();
    const db = noClaimContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A")));
    await assertFails(
      setDoc(doc(db, "tenants/A/staff/onboardingUid"), { role: "assistant", name: "Onboarding" })
    );
  });
});

describe("patient creation", () => {
  it("lets the owner and the receptionist create a patient", async () => {
    await seedFixtures();

    await assertSucceeds(
      setDoc(doc(ownerAContext().firestore(), "tenants/A/patients/patientA2"), {
        name: "Carol Cherry",
        phone: "+911234500003",
        address: "2 Main St, Bengaluru",
        allergies: [],
        medicalHistoryNotes: "",
        createdByUid: "ownerA",
      })
    );
    await assertSucceeds(
      setDoc(doc(receptionistAContext().firestore(), "tenants/A/patients/patientA3"), {
        name: "Dave Date",
        phone: "+911234500004",
        address: "3 Main St, Bengaluru",
        createdByUid: "receptionistA",
      })
    );
  });

  it("rejects a receptionist-authored create that seeds a clinical field", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertFails(
      setDoc(doc(db, "tenants/A/patients/patientA4"), {
        name: "Eve Elder",
        phone: "+911234500005",
        allergies: ["penicillin"],
        createdByUid: "receptionistA",
      })
    );
  });

  it("does not let an assistant create a patient at all", async () => {
    await seedFixtures();
    const db = assistantAContext().firestore();

    await assertFails(
      setDoc(doc(db, "tenants/A/patients/patientA5"), {
        name: "Frank Fig",
        phone: "+911234500006",
        createdByUid: "assistantA",
      })
    );
  });
});

describe("patient records - role-based field restrictions", () => {
  it("lets a receptionist write demographic fields, but rejects a write that also touches a clinical field", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();
    const patientRef = doc(db, "tenants/A/patients/patientA1");

    await assertSucceeds(updateDoc(patientRef, { phone: "+911234509999", address: "New address" }));
    await assertFails(
      updateDoc(patientRef, { phone: "+911234508888", allergies: ["penicillin"] })
    );
    await assertFails(updateDoc(patientRef, { medicalHistoryNotes: "sneaked in by front desk" }));
  });

  it("lets an assistant write clinical fields, but rejects a write that also touches a demographic field", async () => {
    await seedFixtures();
    const db = assistantAContext().firestore();
    const patientRef = doc(db, "tenants/A/patients/patientA1");

    await assertSucceeds(
      updateDoc(patientRef, {
        allergies: ["penicillin"],
        medicalHistoryNotes: "Penicillin allergy noted 2026-01-01",
      })
    );
    await assertFails(updateDoc(patientRef, { allergies: ["latex"], name: "Alice A. Apple" }));
    await assertFails(updateDoc(patientRef, { phone: "+911234507777" }));
  });

  it("CORE: rejects a Lab Coordinator's read and write of any patient document outright", async () => {
    await seedFixtures();
    const db = labCoordinatorAContext().firestore();
    const patientRef = doc(db, "tenants/A/patients/patientA1");

    await assertFails(getDoc(patientRef));
    await assertFails(updateDoc(patientRef, { medicalHistoryNotes: "should never land" }));
    await assertFails(
      setDoc(doc(db, "tenants/A/patients/patientA6"), { name: "Grace Grape", phone: "+91123" })
    );
  });
});

describe("patient consents - tenant isolation", () => {
  it("denies clinic A staff any read or write of clinic B's patient consents", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();
    const consentRef = doc(db, "tenants/B/patients/patientB1/consents/whatsappMarketing");

    await assertFails(getDoc(consentRef));
    await assertFails(updateDoc(consentRef, { granted: true }));
  });

  it("lets the owner and receptionist read and record clinic A's own patient consents", async () => {
    await seedFixtures();
    const ownerDb = ownerAContext().firestore();
    const receptionistDb = receptionistAContext().firestore();

    await assertSucceeds(
      getDoc(doc(ownerDb, "tenants/A/patients/patientA1/consents/whatsappMarketing"))
    );
    await assertSucceeds(
      setDoc(doc(receptionistDb, "tenants/A/patients/patientA1/consents/reviewRequests"), {
        consentType: "reviewRequests",
        granted: true,
        grantedAt: "2026-01-02T00:00:00Z",
        revokedAt: null,
        signatureUrl: null,
        recordedByUid: "receptionistA",
      })
    );
  });
});

describe("scheduling - appointments", () => {
  it("lets the receptionist create, edit, and cancel an appointment within their own clinic", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertSucceeds(
      setDoc(doc(db, "tenants/A/appointments/appointmentA2"), {
        patientId: "patientA1",
        patientName: "Alice Apple",
        dentistUid: "ownerA",
        startTime: "2026-02-02T09:00:00Z",
        endTime: "2026-02-02T09:30:00Z",
        status: "scheduled",
        source: "walkIn",
        notes: "",
        createdByUid: "receptionistA",
        createdAt: "2026-01-16T00:00:00Z",
        cancelledReason: null,
      })
    );

    const appointmentRef = doc(db, "tenants/A/appointments/appointmentA1");
    await assertSucceeds(updateDoc(appointmentRef, { status: "confirmed" }));
    await assertSucceeds(
      updateDoc(appointmentRef, { status: "cancelled", cancelledReason: "Patient requested" })
    );
  });

  it("lets the assistant read an appointment but rejects any write attempt", async () => {
    await seedFixtures();
    const db = assistantAContext().firestore();

    await assertSucceeds(getDoc(doc(db, "tenants/A/appointments/appointmentA1")));
    await assertFails(
      updateDoc(doc(db, "tenants/A/appointments/appointmentA1"), { status: "confirmed" })
    );
    await assertFails(
      setDoc(doc(db, "tenants/A/appointments/appointmentA2"), {
        patientId: "patientA1",
        patientName: "Alice Apple",
        dentistUid: "ownerA",
        startTime: "2026-02-02T09:00:00Z",
        endTime: "2026-02-02T09:30:00Z",
        status: "scheduled",
        source: "walkIn",
        notes: "",
        createdByUid: "assistantA",
        createdAt: "2026-01-16T00:00:00Z",
        cancelledReason: null,
      })
    );
  });

  it("CORE: rejects a Lab Coordinator's read and write of appointments and waitlist entries outright", async () => {
    await seedFixtures();
    const db = labCoordinatorAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/appointments/appointmentA1")));
    await assertFails(
      updateDoc(doc(db, "tenants/A/appointments/appointmentA1"), { status: "confirmed" })
    );
    await assertFails(getDoc(doc(db, "tenants/A/waitlist/waitlistA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/waitlist/waitlistA1"), { status: "offered" }));
  });

  it("CORE: denies clinic A staff any read or write of clinic B's appointments and waitlist", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/B/appointments/appointmentB1")));
    await assertFails(
      updateDoc(doc(db, "tenants/B/appointments/appointmentB1"), { status: "confirmed" })
    );
    await assertFails(
      setDoc(doc(db, "tenants/B/waitlist/intruderEntry"), {
        patientId: "patientB1",
        patientName: "Bob Banana",
        preferredDates: [],
        notes: "",
        addedAt: "2026-01-16T00:00:00Z",
        addedByUid: "ownerA",
        status: "waiting",
      })
    );
  });

  it("rejects an appointment create or reschedule where endTime is not after startTime", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertFails(
      setDoc(doc(db, "tenants/A/appointments/appointmentBackwards"), {
        patientId: "patientA1",
        patientName: "Alice Apple",
        dentistUid: "ownerA",
        startTime: "2026-02-02T09:30:00Z",
        endTime: "2026-02-02T09:00:00Z",
        status: "scheduled",
        source: "walkIn",
        notes: "",
        createdByUid: "receptionistA",
        createdAt: "2026-01-16T00:00:00Z",
        cancelledReason: null,
      })
    );
    await assertFails(
      updateDoc(doc(db, "tenants/A/appointments/appointmentA1"), {
        startTime: "2026-02-01T09:30:00Z",
        endTime: "2026-02-01T09:00:00Z",
      })
    );
  });

  it("rejects an appointment or waitlist write that references a patientId not in this tenant", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertFails(
      setDoc(doc(db, "tenants/A/appointments/appointmentGhost"), {
        patientId: "noSuchPatient",
        patientName: "Ghost Patient",
        dentistUid: "ownerA",
        startTime: "2026-02-02T09:00:00Z",
        endTime: "2026-02-02T09:30:00Z",
        status: "scheduled",
        source: "walkIn",
        notes: "",
        createdByUid: "receptionistA",
        createdAt: "2026-01-16T00:00:00Z",
        cancelledReason: null,
      })
    );
    await assertFails(
      setDoc(doc(db, "tenants/A/waitlist/waitlistGhost"), {
        patientId: "noSuchPatient",
        patientName: "Ghost Patient",
        preferredDates: [],
        notes: "",
        addedAt: "2026-01-16T00:00:00Z",
        addedByUid: "receptionistA",
        status: "waiting",
      })
    );
  });
});

describe("charting - odontogram & periodontal", () => {
  it("lets the owner/dentist write both toothConditions and periodontalChart", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();
    const chartingRef = doc(db, "tenants/A/chartings/chartingA1");

    await assertSucceeds(
      updateDoc(chartingRef, {
        toothConditions: { "11": { surfaces: { occlusal: "filled" }, notes: "" } },
        lastEditedByUid: "ownerA",
      })
    );
    await assertSucceeds(
      updateDoc(chartingRef, {
        periodontalChart: {
          "11": { pocketDepths: [3, 3, 3, 3, 3, 3], bleeding: [true, false, false, false, false, false], mobilityGrade: 1 },
        },
        lastEditedByUid: "ownerA",
      })
    );
  });

  it("lets the assistant write periodontalChart alone, but rejects a write that also touches toothConditions", async () => {
    await seedFixtures();
    const db = assistantAContext().firestore();
    const chartingRef = doc(db, "tenants/A/chartings/chartingA1");

    await assertSucceeds(
      updateDoc(chartingRef, {
        periodontalChart: {
          "11": { pocketDepths: [4, 3, 3, 3, 3, 4], bleeding: [true, true, false, false, false, false], mobilityGrade: 1 },
        },
        lastEditedByUid: "assistantA",
      })
    );
    await assertFails(
      updateDoc(chartingRef, {
        periodontalChart: {
          "11": { pocketDepths: [4, 3, 3, 3, 3, 4], bleeding: [true, true, false, false, false, false], mobilityGrade: 1 },
        },
        toothConditions: { "11": { surfaces: { occlusal: "crown" }, notes: "sneaked in by hygienist" } },
        lastEditedByUid: "assistantA",
      })
    );
  });

  it("handles dotted-path partial map updates the same way as full-map replacement", async () => {
    // The real ChartingRepository writes single-tooth entries via dotted field paths
    // (e.g. "periodontalChart.11") rather than rewriting the whole map, so this
    // verifies that shape specifically, not just an equivalent full-map replacement.
    await seedFixtures();
    const ownerDb = ownerAContext().firestore();
    const assistantDb = assistantAContext().firestore();
    const chartingRef = (db: typeof ownerDb) => doc(db, "tenants/A/chartings/chartingA1");

    await assertSucceeds(
      updateDoc(chartingRef(assistantDb), {
        "periodontalChart.12": { pocketDepths: [2, 2, 2, 2, 2, 2], bleeding: [false, false, false, false, false, false], mobilityGrade: 0 },
        lastEditedByUid: "assistantA",
      })
    );
    await assertFails(
      updateDoc(chartingRef(assistantDb), {
        "toothConditions.12": { surfaces: { occlusal: "caries" }, notes: "" },
        lastEditedByUid: "assistantA",
      })
    );
    await assertSucceeds(
      updateDoc(chartingRef(ownerDb), {
        "toothConditions.12": { surfaces: { occlusal: "caries" }, notes: "" },
        lastEditedByUid: "ownerA",
      })
    );
  });

  it("CORE: rejects the assistant's write of a treatmentPlan outright, though their read succeeds", async () => {
    await seedFixtures();
    const db = assistantAContext().firestore();

    await assertSucceeds(getDoc(doc(db, "tenants/A/treatmentPlans/planA1")));
    await assertFails(
      updateDoc(doc(db, "tenants/A/treatmentPlans/planA1"), { status: "proposed" })
    );
    await assertFails(
      setDoc(doc(db, "tenants/A/treatmentPlans/planA2"), {
        patientId: "patientA1",
        createdByUid: "assistantA",
        createdAt: "2026-02-02T00:00:00Z",
        status: "draft",
        lineItems: [],
        totalEstimate: 0,
        patientApprovalSignatureUrl: null,
        patientApprovedAt: null,
      })
    );
  });

  it("CORE: rejects the receptionist's read and write of chartings and treatmentPlans outright", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/chartings/chartingA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/chartings/chartingA1"), { toothConditions: {} }));
    await assertFails(getDoc(doc(db, "tenants/A/treatmentPlans/planA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/treatmentPlans/planA1"), { status: "proposed" }));
  });

  it("CORE: rejects the Lab Coordinator's read and write of chartings and treatmentPlans outright", async () => {
    await seedFixtures();
    const db = labCoordinatorAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/chartings/chartingA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/chartings/chartingA1"), { toothConditions: {} }));
    await assertFails(getDoc(doc(db, "tenants/A/treatmentPlans/planA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/treatmentPlans/planA1"), { status: "proposed" }));
  });

  it("CORE: denies clinic A staff any read or write of clinic B's chartings and treatmentPlans", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/B/chartings/chartingB1")));
    await assertFails(updateDoc(doc(db, "tenants/B/chartings/chartingB1"), { toothConditions: {} }));
    await assertFails(getDoc(doc(db, "tenants/B/treatmentPlans/planB1")));
    await assertFails(updateDoc(doc(db, "tenants/B/treatmentPlans/planB1"), { status: "proposed" }));
  });
});

describe("imaging - X-ray/photo metadata", () => {
  it("lets the owner and the assistant create, read, and update (tag) an imaging record", async () => {
    await seedFixtures();
    const ownerDb = ownerAContext().firestore();
    const assistantDb = assistantAContext().firestore();

    await assertSucceeds(
      setDoc(doc(assistantDb, "tenants/A/imaging/imagingA2"), {
        patientId: "patientA1",
        toothNumber: "21",
        type: "intraoralPhoto",
        storageUrl: "https://example.com/imagingA2.jpg",
        capturedAt: "2026-02-02T00:00:00Z",
        uploadedByUid: "assistantA",
        notes: null,
      })
    );
    await assertSucceeds(getDoc(doc(ownerDb, "tenants/A/imaging/imagingA1")));
    await assertSucceeds(updateDoc(doc(assistantDb, "tenants/A/imaging/imagingA1"), { notes: "Retake next visit" }));
    await assertSucceeds(updateDoc(doc(ownerDb, "tenants/A/imaging/imagingA1"), { type: "CBCT" }));
  });

  it("CORE: rejects the assistant's delete of an imaging record, but the owner's delete succeeds", async () => {
    await seedFixtures();
    const assistantDb = assistantAContext().firestore();
    const ownerDb = ownerAContext().firestore();

    await assertFails(deleteDoc(doc(assistantDb, "tenants/A/imaging/imagingA1")));
    await assertSucceeds(deleteDoc(doc(ownerDb, "tenants/A/imaging/imagingA1")));
  });

  it("CORE: rejects the receptionist's read and write of imaging records outright", async () => {
    await seedFixtures();
    const db = receptionistAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/imaging/imagingA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/imaging/imagingA1"), { notes: "sneaked in" }));
    await assertFails(
      setDoc(doc(db, "tenants/A/imaging/imagingGhost"), {
        patientId: "patientA1",
        toothNumber: null,
        type: "RVG",
        storageUrl: "https://example.com/ghost.jpg",
        capturedAt: "2026-02-02T00:00:00Z",
        uploadedByUid: "receptionistA",
        notes: null,
      })
    );
  });

  it("CORE: rejects the Lab Coordinator's read and write of imaging records outright", async () => {
    await seedFixtures();
    const db = labCoordinatorAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/imaging/imagingA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/imaging/imagingA1"), { notes: "sneaked in" }));
  });

  it("CORE: denies clinic A staff any read or write of clinic B's imaging records", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/B/imaging/imagingB1")));
    await assertFails(updateDoc(doc(db, "tenants/B/imaging/imagingB1"), { notes: "sneaked in" }));
    await assertFails(deleteDoc(doc(db, "tenants/B/imaging/imagingB1")));
  });

  it("rejects an imaging record that references a patientId not in this tenant", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(
      setDoc(doc(db, "tenants/A/imaging/imagingGhost"), {
        patientId: "noSuchPatient",
        toothNumber: null,
        type: "RVG",
        storageUrl: "https://example.com/ghost.jpg",
        capturedAt: "2026-02-02T00:00:00Z",
        uploadedByUid: "ownerA",
        notes: null,
      })
    );
  });
});

describe("invoices - GST billing", () => {
  function invoicePayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
    return {
      patientId: "patientA1",
      invoiceNumber: "INV-0002",
      issuedAt: "2026-02-06T00:00:00Z",
      issuedByUid: "receptionistA",
      lineItems: [],
      billingState: "Karnataka",
      subtotal: 0,
      cgst: 0,
      sgst: 0,
      igst: 0,
      total: 0,
      paymentStatus: "unpaid",
      amountPaid: 0,
      razorpayPaymentLinkId: null,
      razorpayStatus: null,
      treatmentPlanId: null,
      ...overrides,
    };
  }

  it("lets the owner and the receptionist create, read, and update an invoice", async () => {
    await seedFixtures();
    const ownerDb = ownerAContext().firestore();
    const receptionistDb = receptionistAContext().firestore();

    await assertSucceeds(
      setDoc(doc(receptionistDb, "tenants/A/invoices/invoiceA2"), invoicePayload())
    );
    await assertSucceeds(getDoc(doc(ownerDb, "tenants/A/invoices/invoiceA1")));
    await assertSucceeds(
      updateDoc(doc(receptionistDb, "tenants/A/invoices/invoiceA1"), { paymentStatus: "partial", amountPaid: 500 })
    );
    await assertSucceeds(
      updateDoc(doc(ownerDb, "tenants/A/invoices/invoiceA1"), { paymentStatus: "paid", amountPaid: 1770 })
    );
  });

  it("lets the owner and the receptionist void (delete) an invoice", async () => {
    await seedFixtures();
    const ownerDb = ownerAContext().firestore();
    const receptionistDb = receptionistAContext().firestore();

    await assertSucceeds(
      setDoc(doc(receptionistDb, "tenants/A/invoices/invoiceToVoid"), invoicePayload())
    );
    await assertSucceeds(deleteDoc(doc(ownerDb, "tenants/A/invoices/invoiceToVoid")));
  });

  it("CORE: rejects the assistant's read AND write of invoices outright - a clean full exclusion, not a partial one", async () => {
    await seedFixtures();
    const db = assistantAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/invoices/invoiceA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/invoices/invoiceA1"), { paymentStatus: "paid" }));
    await assertFails(setDoc(doc(db, "tenants/A/invoices/invoiceGhost"), invoicePayload()));
    await assertFails(deleteDoc(doc(db, "tenants/A/invoices/invoiceA1")));
  });

  it("CORE: rejects the Lab Coordinator's read AND write of invoices outright", async () => {
    await seedFixtures();
    const db = labCoordinatorAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/A/invoices/invoiceA1")));
    await assertFails(updateDoc(doc(db, "tenants/A/invoices/invoiceA1"), { paymentStatus: "paid" }));
    await assertFails(setDoc(doc(db, "tenants/A/invoices/invoiceGhost"), invoicePayload()));
  });

  it("CORE: denies clinic A staff any read or write of clinic B's invoices", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(getDoc(doc(db, "tenants/B/invoices/invoiceB1")));
    await assertFails(updateDoc(doc(db, "tenants/B/invoices/invoiceB1"), { paymentStatus: "paid" }));
    await assertFails(deleteDoc(doc(db, "tenants/B/invoices/invoiceB1")));
  });

  it("rejects an invoice that references a patientId not in this tenant", async () => {
    await seedFixtures();
    const db = ownerAContext().firestore();

    await assertFails(
      setDoc(doc(db, "tenants/A/invoices/invoiceGhost"), invoicePayload({ patientId: "noSuchPatient" }))
    );
  });
});
