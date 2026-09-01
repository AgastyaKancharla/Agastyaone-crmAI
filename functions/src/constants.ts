/** Cloud Functions region. Must match the Firestore/Storage location (asia-south1, Mumbai). */
export const REGION = "asia-south1";

/**
 * Mirrors app/src/main/java/com/agastyaone/crmai/core/Role.kt - keep both in sync,
 * these are the only values the `role` custom claim and Firestore rules recognize.
 */
export const CLINIC_ROLES = [
  "owner",
  "receptionist",
  "assistant",
  "labCoordinator",
] as const;

export type ClinicRole = (typeof CLINIC_ROLES)[number];

export const INVITABLE_ROLES: ClinicRole[] = [
  "receptionist",
  "assistant",
  "labCoordinator",
];

export function isClinicRole(value: unknown): value is ClinicRole {
  return typeof value === "string" && (CLINIC_ROLES as readonly string[]).includes(value);
}
