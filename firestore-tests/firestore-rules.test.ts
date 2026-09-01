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
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
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
