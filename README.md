# AgastyaOne CRM — Phase 1: Foundation

Firebase wiring, authentication, role-based access, and tenant isolation for the
AgastyaOne dental clinic CRM. No patient management, scheduling, or other clinical
modules yet — this phase only builds the foundation those will sit on.

## What's here

- **`app/`** — Android app (Kotlin + Jetpack Compose). Auth screens, role-based
  dashboard shells, and the client side of the invite flow.
- **`functions/`** — Cloud Functions (TypeScript) that own every custom-claims
  mutation: `createClinicAndOwner`, `inviteStaff`, `acceptInvite`, `updateStaffRole`,
  and `onPlatformAdminWrite`.
- **`firestore.rules`**, **`storage.rules`**, **`firestore.indexes.json`** — security
  rules and indexes for this phase's collections.
- **`firebase.json`**, **`.firebaserc`** — Firebase CLI project config.

## 1. Create the Firebase project

1. Go to the [Firebase console](https://console.firebase.google.com) and create a new
   project (or use an existing GCP project).
2. **Firestore**: Build → Firestore Database → Create database → select
   **`asia-south1` (Mumbai)** as the location. This is a one-time, unchangeable choice
   made in the console (or via `gcloud firestore databases create --location=asia-south1`)
   — it cannot be set from `firebase.json`. Do not accept the default `nam5`/`us-central`
   location.
3. **Storage**: Build → Storage → Get started → also select **`asia-south1`**.
4. **Authentication**: Build → Authentication → Sign-in method → enable **Phone** and
   **Email/Password**.
5. **App Check**: Build → App Check → register your Android app → enable the
   **Play Integrity** provider. (You'll need a debug token for local development on an
   emulator/unregistered device — App Check → Apps → your app → "Manage debug tokens".)
6. Register an Android app in Project settings with package name
   `com.agastyaone.crmai`, and download `google-services.json`.

## 2. Place `google-services.json`

`app/google-services.json` is already committed, pointing at the `agastyaone-crm`
Firebase project - the API key in this file identifies the project to Google's
services but isn't a secret (Firebase security comes from Security Rules and App
Check, not from hiding this file), so it's safe to keep in the repo. If you point
this app at a different Firebase project, download your own from Project settings
and overwrite the file at:

```
app/google-services.json
```

(Next to `app/build.gradle.kts`.)

## 3. Wire up the Firebase CLI

```bash
npm install -g firebase-tools   # if you don't have it
firebase login
```

Edit `.firebaserc` and replace `REPLACE_WITH_YOUR_FIREBASE_PROJECT_ID` with your real
project ID.

## 4. Seed the first platform admin (manual, one-time)

Platform admin is deliberately not reachable from the clinic signup/invite flow. For
this phase, grant it by hand: create a Firebase Auth user for your AgastyaOne team
member (console → Authentication → Add user, email/password), then create a Firestore
document at `platformAdmins/{that user's uid}` with `{ name, email }`. The
`onPlatformAdminWrite` Cloud Function will pick that up and set the `platformAdmin`
custom claim automatically. They can then sign in via the app's "AgastyaOne team
sign-in" entry point.

## 5. Deploy rules, indexes, and functions

```bash
cd functions && npm install && cd ..
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

## 6. Build the Android app

Open the repo root in Android Studio (Iguana+) and let it sync, or from the CLI:

```bash
./gradlew :app:assembleDebug
```

> **Note on this PR:** the sandbox this was built in has no Android SDK and its
> network policy blocks `dl.google.com` (the Android Gradle Plugin's Maven repo), so
> the Android module could not be compiled here. The Cloud Functions were compiled
> (`tsc`) and linted (`eslint`) successfully. Please run a full `./gradlew build` (or
> open in Android Studio) as part of reviewing this PR.

## Data model (this phase)

```
tenants/{clinicId}
  clinicName, address, city, state, gstin, subscriptionStatus, createdAt, ownerUid

tenants/{clinicId}/staff/{uid}
  name, role, phone, email, active, invitedAt, joinedAt, invitedByUid

tenants/{clinicId}/invites/{inviteId}
  inviteId, role, name, phone, email, status, invitedByUid, invitedAt,
  acceptedAt, acceptedByUid

tenants/{clinicId}/auditLog/{logId}
  actionType, performedByUid, targetCollection, targetDocId, timestamp, details

platformAdmins/{uid}
  name, email
```

`role` and `clinicId` (and `platformAdmin`) live as Firebase Auth **custom claims**,
not just Firestore fields — that's what both the security rules and the app's routing
trust. Only the Cloud Functions in `functions/src` ever set them.

## Auth flows

- **Owner/Dentist signup** (`OwnerSignupScreen`): collects clinic details + a
  phone-OTP or email/password credential, then calls `createClinicAndOwner`, which
  creates the `tenants/{clinicId}` doc and the owner's own `staff` doc in one
  transaction and sets `{ role: "owner", clinicId }` claims.
- **Staff invite**: an owner invites a Receptionist/Assistant/Lab Coordinator by
  phone or email from the "Staff" tile on their dashboard (`InviteStaffScreen` →
  `inviteStaff`). The invited person signs in with that phone/email
  (`StaffSignInScreen`) and lands on "Waiting for clinic setup", where "I have a staff
  invite" calls `acceptInvite` — it looks up the pending invite by their verified
  phone/email, sets their claims, creates their `staff` doc, and marks the invite
  accepted.
- **Platform admin**: a separate "AgastyaOne team sign-in" entry point
  (`PlatformAdminSignInScreen`), email/password only, checked against the
  `platformAdmin` claim after sign-in — never reachable from the owner-signup or
  invite flow.

## Role-based routing

After sign-in, `AgastyaOneRoot` routes purely off the resolved custom claims:

| Session state | Screen |
|---|---|
| `owner` | Full dashboard: Schedule, Patients, Clinical, Billing, Reports, Inventory, Lab, Staff |
| `receptionist` | Schedule, Billing only |
| `assistant` | Patients (view-only placeholder) only |
| `labCoordinator` | Lab Orders only |
| Signed in, no `role`/`clinicId` claim yet | "Waiting for clinic setup" |
| Platform admin | Separate platform-admin shell |

Hidden tiles are absent from the grid, not just disabled.

## Manual test plan ("done" criteria)

1. Sign up as an Owner → land on the full dashboard shell.
2. From the Owner dashboard's Staff tile, invite a Receptionist by phone or email.
3. Sign in as that Receptionist (same phone/email) → "Waiting for clinic setup" → tap
   "I have a staff invite" → land on the Receptionist shell (Schedule + Billing only).
4. As the Receptionist, try to read another tenant's `tenants/{otherClinicId}` doc
   directly — the security rules reject it (`PERMISSION_DENIED`).
5. Check `tenants/{clinicId}/auditLog` in the console — entries exist for
   `clinic_created` and `staff_invite_sent`/`staff_invite_accepted`.

## Phase 3a — Odontogram, periodontal charting & treatment planning

- **Chartings** (`tenants/{clinicId}/chartings`): the odontogram (`toothConditions`) is
  dentist/owner-only to write; the periodontal chart (`periodontalChart`) can be written
  by the dentist/owner *or* an assistant/hygienist (routine pocket-depth/bleeding/mobility
  readings are their job) - enforced with the same field-level rule pattern as Phase 2a's
  patient-record split. Receptionist and Lab Coordinator have no access at all.
- **Treatment plans** (`tenants/{clinicId}/treatmentPlans`): dentist/owner-only for every
  write (create, edit, and patient-approval signing); assistant gets read-only access,
  everyone else none.
- **Tooth numbering**: chart data is always stored keyed by FDI tooth number. Display as
  Universal instead is a per-clinic, per-tenant setting (`tenants/{clinicId}.toothNumberingSystem`,
  `"FDI"` or `"Universal"`) - a display-layer conversion only, so switching it later never
  renumbers or orphans existing chart data.

> **⚠️ Billing caveat**: the treatment-plan line-item picker's seed procedure list
> (`ProcedureCatalog.SEED_PROCEDURES`) uses internal placeholder codes (e.g. `"RCT"`,
> `"SCALING"`) that are **not** GST HSN/SAC codes and carry no default pricing. Before
> this ever feeds a real invoice (a later phase), the clinic's own accountant must verify
> and map every procedure to its correct HSN/SAC code and tax rate. Treating the seed list
> as billing-ready as-is would be a compliance mistake.

## Phase 3b — X-ray & imaging

- **Imaging** (`tenants/{clinicId}/imaging`): owner/dentist and assistant/hygienist can
  upload and tag (tooth number, type, notes); only the owner/dentist can delete a record,
  same real-world split as periodontal charting in Phase 3a. Receptionist and Lab
  Coordinator have no Firestore or Storage access at all - not even to a raw file URL, since
  Storage rules (`tenants/{clinicId}/patients/{patientId}/imaging/...`) mirror the same
  role/tenant restrictions as the Firestore document.
- **Format scope**: JPEG/PNG only (camera capture and gallery/Photo Picker selection).
  **DICOM is an explicit stretch goal, not built in this phase** - CBCT/RVG/OPG hardware
  that exports DICOM natively would need a separate ingestion and viewer path (windowing,
  multi-frame series) that this phase's simple image pipeline doesn't attempt.
- **Before/after comparison**: side-by-side view of any two images with independent
  pinch-zoom per pane. A slider/overlay comparison mode was considered and deliberately
  left out - side-by-side is the spec's minimum bar, and overlay alignment (registration
  between two photos taken at different times/angles) is a meaningfully bigger feature than
  this phase's scope.
- This closes out **Core Clinical Tools**. Next module: **Billing & Financial Management**.

## Phase 4a — GST invoicing

- **Invoices** (`tenants/{clinicId}/invoices`): owner/dentist and receptionist have full
  access (create, edit, void, record payments) - the spec is explicit that billing is a
  receptionist-level duty here, same as the owner. Assistant/hygienist and Lab Coordinator
  get a **clean full exclusion**: no partial-field carve-out like the clinical modules,
  both their read and write are rejected outright.
- **Sequential invoice numbers** (`INV-0001`, `INV-0002`, ...) are assigned inside the same
  Firestore transaction that creates the invoice, bumping an `invoiceCounter` field on the
  tenant doc - race-safe under concurrent staff members, unlike a naive "count existing
  invoices + 1". firestore.rules gives the receptionist a narrow, field-scoped allowance
  (`touchesOnly(['invoiceCounter'])`) to touch just that one field on the otherwise
  owner-only tenant document.
- **GST split**: same state as the clinic's own `tenants/{clinicId}.state` → CGST + SGST
  (each half the total rate); different state → IGST, full rate. Most patients are local to
  the clinic, so `billingState` simply defaults to the clinic's state and is editable per
  invoice, rather than building a full address-collection flow.
- **⚠️ HSN/SAC codes and GST rate are placeholders, not verified for filing**:
  `ProcedureCatalog.Procedure.hsnSacCode` defaults every seed procedure to SAC 999319
  ("Other human health services") as a stand-in, and the GST calculation uses an 18%
  placeholder rate (`DEFAULT_GST_RATE_PERCENT`) since the spec doesn't name a rate and many
  dental/medical services are actually GST-exempt under Indian law. Both are flagged here,
  in the `ProcedureCatalog` doc comment, and as a footnote printed on every generated
  invoice PDF - the clinic's own accountant must confirm the correct code and rate per
  procedure before this is used for real GST filing.
- **PDF + sharing**: a real, single-page PDF is rendered with `android.graphics.pdf.PdfDocument`
  (part of the Android SDK, no new dependency) and handed to the standard Android share
  sheet (`Intent.ACTION_SEND`) - WhatsApp, email, Drive etc. all show up there naturally.
  This is explicitly *not* the Meta Cloud API WhatsApp integration (a later
  Communication & Automation phase); staff tap send manually from the share sheet.
- Payment status (`unpaid` / `partial` / `paid`) and `amountPaid` are recorded manually,
  independent of Phase 4b's future Razorpay integration - most patients still pay by cash
  or card in person.
- Razorpay payment links (Phase 4b) and insurance/TPA claims (Phase 4c) are explicitly out
  of scope for this phase.
