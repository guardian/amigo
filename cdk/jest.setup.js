/* eslint-disable no-undef -- jest globals are missing due to guardian.recommended.jest eslint rules not covering jest.setup.js */
jest.mock("@guardian/cdk/lib/constants/tracking-tag");
// Fixed test fixture, never a deployable image reference.
process.env.IMAGE_DIGEST = `sha256:${"0".repeat(64)}`;
