import { HttpsError, onCall } from "firebase-functions/v2/https";
import { FieldValue, QueryDocumentSnapshot } from "firebase-admin/firestore";
import { auth, db } from "./admin";
import { REGION } from "./constants";
import { writeAuditLog } from "./audit";

interface AcceptInviteRequest {
  inviteId?: string | null;
}

async function findInvite(
  uid: string,
  inviteId: string | null | undefined
): Promise<QueryDocumentSnapshot> {
  if (inviteId) {
    const byId = await db
      .collectionGroup("invites")
      .where("inviteId", "==", inviteId)
      .where("status", "==", "pending")
      .limit(1)
      .get();
    if (!byId.empty) return byId.docs[0];
    throw new HttpsError("not-found", "That invite is no longer pending.");
  }

  const user = await auth.getUser(uid);

  if (user.phoneNumber) {
    const byPhone = await db
      .collectionGroup("invites")
      .where("phone", "==", user.phoneNumber)
      .where("status", "==", "pending")
      .limit(1)
      .get();
    if (!byPhone.empty) return byPhone.docs[0];
  }

  if (user.email) {
    const byEmail = await db
      .collectionGroup("invites")
      .where("email", "==", user.email)
      .where("status", "==", "pending")
      .limit(1)
      .get();
    if (!byEmail.empty) return byEmail.docs[0];
  }

  throw new HttpsError(
    "not-found",
    "No pending invite matches this account's phone number or email."
  );
}

/**
 * Called by the invited person once they've signed in with the phone/email the owner
 * invited. Sets their custom claims to match the invite, creates their staff doc, and
 * marks the invite accepted - all with the Admin SDK, so none of this needs a Firestore
 * rule allowing staff writes to an invite they don't own yet.
 */
export const acceptInvite = onCall(
  { region: REGION, enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in first.");
    }
    const uid = request.auth.uid;

    if (request.auth.token.clinicId || request.auth.token.role) {
      throw new HttpsError(
        "failed-precondition",
        "This account is already linked to a clinic."
      );
    }

    const data = request.data as AcceptInviteRequest;
    const inviteDoc = await findInvite(uid, data.inviteId);
    const invite = inviteDoc.data();
    const clinicRef = inviteDoc.ref.parent.parent;
    if (!clinicRef) {
      throw new HttpsError("internal", "Invite is missing its parent clinic.");
    }
    const clinicId = clinicRef.id;
    const staffRef = clinicRef.collection("staff").doc(uid);

    await db.runTransaction(async (transaction) => {
      transaction.set(staffRef, {
        name: invite.name,
        role: invite.role,
        phone: invite.phone,
        email: invite.email,
        active: true,
        invitedAt: invite.invitedAt,
        joinedAt: FieldValue.serverTimestamp(),
        invitedByUid: invite.invitedByUid,
      });
      transaction.update(inviteDoc.ref, {
        status: "accepted",
        acceptedAt: FieldValue.serverTimestamp(),
        acceptedByUid: uid,
      });
    });

    await auth.setCustomUserClaims(uid, { role: invite.role, clinicId });

    await writeAuditLog({
      clinicId,
      actionType: "staff_invite_accepted",
      performedByUid: uid,
      targetCollection: "staff",
      targetDocId: uid,
      details: { role: invite.role, inviteId: inviteDoc.id },
    });

    return { clinicId, role: invite.role };
  }
);
