import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { auth } from "./admin";
import { REGION } from "./constants";

/**
 * `platformAdmins/{uid}` documents are seeded by AgastyaOne's own team (console or a
 * trusted script), never through the clinic signup/invite flow. This trigger is what
 * turns that Firestore doc into the trusted `platformAdmin` custom claim the security
 * rules and the app's routing actually check.
 */
export const onPlatformAdminWrite = onDocumentWritten(
  { document: "platformAdmins/{uid}", region: REGION },
  async (event) => {
    const uid = event.params.uid;
    const existsAfter = event.data?.after.exists ?? false;

    try {
      const user = await auth.getUser(uid);
      const currentClaims = user.customClaims ?? {};
      await auth.setCustomUserClaims(uid, {
        ...currentClaims,
        platformAdmin: existsAfter || undefined,
      });
    } catch (err) {
      logger.warn(
        `platformAdmins/${uid} written but no matching Auth user exists yet; ` +
          "claim will not be set until this doc is re-written after they sign up.",
        err
      );
    }
  }
);
