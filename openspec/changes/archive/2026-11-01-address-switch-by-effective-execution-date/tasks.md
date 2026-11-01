## 1. Postal address rule (test first)

- [x] 1.1 In `PostalAddressRuleTest`, construct the rule without a date and replace the date-dependent tests: an unstructured creditor address (with and without `Ctry`) gets `RR03` for execution on 2026-11-21 and later, an unstructured debtor address gets `RR02`, `TwnNm` without `Ctry` gets `RR03`; keep the structured, hybrid and too-many-lines tests. Verify they fail for the expected reason with `mise exec -- ./gradlew test --tests '*PostalAddressRuleTest'`.
- [x] 1.2 Simplify `PostalAddressRule`: no constructor parameter, no cutover date, no effective date; message "Address must contain TwnNm and Ctry"; update the ABOUTME lines. Verify `*PostalAddressRuleTest` passes.
- [x] 1.3 In `SepaCreditTransferRules`, create the postal address rule once without a date, as before, and drop the address remark from the `today` parameter doc. Verify `mise exec -- ./gradlew test --tests '*SepaCreditTransferRulesTest'` passes.

## 2. Validator, fixtures and end to end

- [x] 2.1 In `PaymentFileValidatorTest`, replace the three fixed-clock address tests with tests on the default clock: a past-dated SCT Inst file with an unstructured creditor address is rejected with `RR03` only (no `DT01`, no `FF01`); an SCT file executed before 22 November 2026 with an unstructured creditor address is rejected with `RR03`. Verify `*PaymentFileValidatorTest` passes.
- [x] 2.2 In `Fixtures`, keep `withUnstructuredCreditorAddress` and `pastDatedInstantWithUnstructuredCreditorAddress` with doc comments that no longer mention a switch, and simplify `withCreditorNameAndAddressViolations` to the default execution date; drop "cutover" from the names of the tests that use it. Verify `mise exec -- ./gradlew compileTestJava` succeeds and `grep -rni cutover src` returns nothing.
- [x] 2.3 In `PaymentFileProcessingEndToEndTest`, add a test with the real clock: a past-dated SCT Inst file with an unstructured creditor address uploaded via SFTP yields a pain.002 `RJCT` with exactly `RR03`, status `REJECTED` and a complete audit trail. Verify `mise exec -- ./gradlew test --tests '*PaymentFileProcessingEndToEndTest'` passes.

## 3. Roadmap and verification

- [x] 3.1 In `docs/roadmap.md`, remove D2 from "Decisions pending" and change Q7 to ask Compliance to confirm the reason codes `RR02`/`RR03` for debtor and creditor addresses without `TwnNm` or `Ctry`. Verify `grep -n '| D2 ' docs/roadmap.md` finds nothing.
- [x] 3.2 Run `mise exec -- ./gradlew build` and verify all tests pass with pristine output, then `openspec validate address-switch-by-effective-execution-date --strict`.
