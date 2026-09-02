import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";

export type AuditActionType =
  | "clinic_created"
  | "staff_invite_sent"
  | "staff_invite_accepted"
  | "role_change"
  | "payment_link_created"
  | "payment_received"
  | "payment_amount_mismatch";

export interface WriteAuditLogParams {
  clinicId: string;
  actionType: AuditActionType;
  performedByUid: string;
  targetCollection: string;
  targetDocId: string;
  details?: Record<string, unknown>;
}

/**
 * Generic, reusable audit log writer for `tenants/{clinicId}/auditLog/{logId}`.
 * Every later phase that mutates tenant state should call this rather than writing
 * its own bespoke log entry shape.
 */
export async function writeAuditLog(params: WriteAuditLogParams): Promise<void> {
  const { clinicId, actionType, performedByUid, targetCollection, targetDocId, details } = params;
  await db.collection("tenants").doc(clinicId).collection("auditLog").add({
    actionType,
    performedByUid,
    targetCollection,
    targetDocId,
    timestamp: FieldValue.serverTimestamp(),
    details: details ?? {},
  });
}
