# DNMS 初期同意設定 / Consent setup

**Status: foundation only — not enforced by the game yet.** This branch introduces a Java consent store, not a working first-launch GUI or command. Do not claim cloning is gated until every entry point has been wired and tested.

- Default is **deny** if the file is absent, invalid or has an unsupported policy version.
- Consent is opt-in for cloning, shared memories and autonomous behavior independently.
- The data subject must grant their own consent; server-operator consent does not authorize copying someone else's real-person data.
- The subject can revoke consent. A revocation must prevent new copies and future sharing when enforcement is integrated. Existing data requires an explicit retention/deletion workflow.
- Settings are stored in `config/doppelganger/consent.properties` with a timestamp and policy version.
- A production implementation must authenticate the actor and identify the actual data subject; UUID equality alone is not authentication.

## Remaining integration tasks
1. Add a first-run, server-side chat consent prompt and `/doppel consent status|grant|revoke` commands with authenticated player context.
2. Gate **all** clone-generation paths, including `/doppel start`, `/doppel dashboard bootstrap`, `/doppel dashboard add`, automatic growth and unattended resume; fail closed.
3. Gate MIP memory sharing and autonomous behavior separately.
4. Specify legacy-save migration, revocation effects on existing NPCs, and consent per subject (not one global flag).
5. Add JUnit tests and Fabric runtime tests for all entry points, revocation, restart and corrupt files.

Consent to data processing does not establish whether NPCs are conscious.
