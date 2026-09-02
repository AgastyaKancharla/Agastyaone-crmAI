/**
 * Rules-unit tests for storage.rules, run against the Storage emulator (see
 * firebase.json / the CI "rules-test" job - never a live project or bucket).
 *
 * First phase (3b) with its own Storage file, not just Firestore documents, so this
 * mirrors firestore-rules.test.ts's structure and discipline: a failing test here means
 * storage.rules is wrong, not the test.
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
import { deleteObject, getBytes, ref, uploadBytes } from "firebase/storage";
import { afterAll, afterEach, beforeAll, describe, it } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ID = "demo-agastyaone-rules-test";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: {
      rules: readFileSync(resolve(__dirname, "../storage.rules"), "utf8"),
      host: "127.0.0.1",
      port: 9199,
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearStorage();
});

const IMAGE_BYTES = new Uint8Array([0xff, 0xd8, 0xff, 0xe0]);
const IMAGE_METADATA = { contentType: "image/jpeg" };

function ownerAContext() {
  return testEnv.authenticatedContext("ownerA", { role: "owner", clinicId: "A" });
}

function assistantAContext() {
  return testEnv.authenticatedContext("assistantA", { role: "assistant", clinicId: "A" });
}

function receptionistAContext() {
  return testEnv.authenticatedContext("receptionistA", { role: "receptionist", clinicId: "A" });
}

function labCoordinatorAContext() {
  return testEnv.authenticatedContext("labCoordinatorA", { role: "labCoordinator", clinicId: "A" });
}

/** Seeds a file with the Admin SDK's privileges, bypassing the rules under test. */
async function seedImage(path: string): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await uploadBytes(ref(context.storage(), path), IMAGE_BYTES, IMAGE_METADATA);
  });
}

describe("imaging storage - X-ray/photo files", () => {
  it("lets the owner and assistant upload and read an imaging file", async () => {
    const ownerStorage = ownerAContext().storage();
    const assistantStorage = assistantAContext().storage();

    await assertSucceeds(
      uploadBytes(
        ref(assistantStorage, "tenants/A/patients/patientA1/imaging/imageA1.jpg"),
        IMAGE_BYTES,
        IMAGE_METADATA,
      ),
    );
    await assertSucceeds(getBytes(ref(ownerStorage, "tenants/A/patients/patientA1/imaging/imageA1.jpg")));
  });

  it("CORE: rejects the assistant's delete of an imaging file, but the owner's delete succeeds", async () => {
    await seedImage("tenants/A/patients/patientA1/imaging/imageA2.jpg");
    const assistantStorage = assistantAContext().storage();
    const ownerStorage = ownerAContext().storage();

    await assertFails(deleteObject(ref(assistantStorage, "tenants/A/patients/patientA1/imaging/imageA2.jpg")));
    await assertSucceeds(deleteObject(ref(ownerStorage, "tenants/A/patients/patientA1/imaging/imageA2.jpg")));
  });

  it("rejects an oversized or non-image upload", async () => {
    const ownerStorage = ownerAContext().storage();

    await assertFails(
      uploadBytes(ref(ownerStorage, "tenants/A/patients/patientA1/imaging/notAnImage.txt"), IMAGE_BYTES, {
        contentType: "text/plain",
      }),
    );
    await assertFails(
      uploadBytes(
        ref(ownerStorage, "tenants/A/patients/patientA1/imaging/tooBig.jpg"),
        new Uint8Array(6 * 1024 * 1024),
        IMAGE_METADATA,
      ),
    );
  });

  it("CORE: rejects the receptionist's read and write of imaging files outright", async () => {
    await seedImage("tenants/A/patients/patientA1/imaging/imageA3.jpg");
    const db = receptionistAContext().storage();

    await assertFails(getBytes(ref(db, "tenants/A/patients/patientA1/imaging/imageA3.jpg")));
    await assertFails(
      uploadBytes(ref(db, "tenants/A/patients/patientA1/imaging/imageA4.jpg"), IMAGE_BYTES, IMAGE_METADATA),
    );
  });

  it("CORE: rejects the Lab Coordinator's read and write of imaging files outright", async () => {
    await seedImage("tenants/A/patients/patientA1/imaging/imageA5.jpg");
    const db = labCoordinatorAContext().storage();

    await assertFails(getBytes(ref(db, "tenants/A/patients/patientA1/imaging/imageA5.jpg")));
    await assertFails(
      uploadBytes(ref(db, "tenants/A/patients/patientA1/imaging/imageA6.jpg"), IMAGE_BYTES, IMAGE_METADATA),
    );
  });

  it("CORE: denies clinic A staff any read or write of clinic B's imaging files, even knowing the storage path", async () => {
    await seedImage("tenants/B/patients/patientB1/imaging/imageB1.jpg");
    const db = ownerAContext().storage();

    await assertFails(getBytes(ref(db, "tenants/B/patients/patientB1/imaging/imageB1.jpg")));
    await assertFails(
      uploadBytes(ref(db, "tenants/B/patients/patientB1/imaging/imageB2.jpg"), IMAGE_BYTES, IMAGE_METADATA),
    );
  });

  it("denies unauthenticated access to an imaging file, even knowing the storage path", async () => {
    await seedImage("tenants/A/patients/patientA1/imaging/imageA7.jpg");
    const db = testEnv.unauthenticatedContext().storage();

    await assertFails(getBytes(ref(db, "tenants/A/patients/patientA1/imaging/imageA7.jpg")));
    await assertFails(
      uploadBytes(ref(db, "tenants/A/patients/patientA1/imaging/imageA8.jpg"), IMAGE_BYTES, IMAGE_METADATA),
    );
  });
});
