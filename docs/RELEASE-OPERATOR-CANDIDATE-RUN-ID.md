# Release operator — choosing a signed CI candidate

This is a procedural aid for future releases. **v1.7.3 is already public**; do not execute a new publication for it to repair historical screenshots or cancelled CI runs.

1. Open [CI](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/workflows/host-tests.yml) and select **Run workflow** on `main` with **sign_main_candidate = true**. The protected `thor-signing` environment, correct original/rerun actor and credential checks are required.
2. Wait for **the entire manual CI run** to reach **completed / success**; confirm that both `build` and `Sign main candidate` succeeded. A green `push` CI run only creates an **unsigned** APK. A `cancelled`, pending or failed run is not a candidate.
3. Confirm the run has one non-expired artifact named `signed-candidate-v<version>-<full-commit-SHA>`, with the intended version and SHA. Copy the **run ID from that successful CI run URL** (not the publication-run ID or the artifact ID).
4. Open [Publish tested candidate](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/workflows/release.yml), run on `main`, paste that CI **run ID** into `candidate_run_id`, and select the intended `channel`. Do not substitute a different ID when validation fails without confirming its release version and signature.
5. The publisher first checks completion, original operator, workflow/repository identity and that **exact signed artifact**. It then rechecks APK version/code, metadata commit, hash and established signing certificate before publishing. If any check fails, do **not** bypass it or rebuild an untracked APK.

**2026-10-17 incident:** the publisher was given a CI run that finished `cancelled` instead of a later successful manually signed run. The original rejection was correct, but the UI guidance and early artifact verification were insufficient. The v1.7.3 publication subsequently succeeded in run [39036944143](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39036944143). That **publication run ID is not a candidate CI ID**.

**Scope of automated validation:** the release workflow validates identity and artifact integrity, not physical Thor display behavior. It cannot establish fixes for [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) or [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84). See [the separate engineering assessment](ISSUES-65-84-ENGINEERING-REVIEW-2026-10-17.md).
