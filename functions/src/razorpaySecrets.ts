import { defineSecret } from "firebase-functions/params";

/**
 * Phase 4b - Razorpay key secret and webhook secret. These are declarations only
 * (a secret *name*, not a value) - the actual values live in Google Secret Manager,
 * set via `firebase functions:secrets:set <NAME>` (or `functions/.secret.local` for
 * local emulator testing, which is git-ignored). Never assign a literal key value to
 * these or any other constant in this codebase.
 */
export const RAZORPAY_KEY_ID = defineSecret("RAZORPAY_KEY_ID");
export const RAZORPAY_KEY_SECRET = defineSecret("RAZORPAY_KEY_SECRET");
export const RAZORPAY_WEBHOOK_SECRET = defineSecret("RAZORPAY_WEBHOOK_SECRET");
