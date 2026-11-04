## 1. Payment information ids in the validation result (unit, test first)

- [x] 1.1 Write a failing test in `PaymentFileValidatorTest`: a valid file with two payment information blocks yields a result carrying both `PmtInfId` values with paths `PmtInf[1]/PmtInfId` and `PmtInf[2]/PmtInfId`, and a schema-invalid file yields an empty list. Verify: `mise exec -- ./gradlew test --tests '*PaymentFileValidatorTest'` fails
- [x] 1.2 Add `PaymentInformationReference(id, path)` and the list to `ValidationResult` (empty for `tooLarge`, `technicalError`, `timedOut`, format and schema failures), and collect the references from the parsed `PaymentInformation` blocks. Verify: `mise exec -- ./gradlew test --tests '*PaymentFileValidatorTest'` passes

## 2. Registry and acceptance logic (integration, test first)

- [x] 2.1 Add Flyway migration `V18__unique_payment_information_id.sql` creating `accepted_payment_information (submitter TEXT, payment_information_id VARCHAR(35), file_id UUID, accepted_at TIMESTAMPTZ DEFAULT now())` with primary key `(submitter, payment_information_id)` (design D1). Verify: the Flyway migration runs in `mise exec -- ./gradlew fullTest --tests '*PaymentFileValidationIntegrationTest'`
- [x] 2.2 Write failing tests in `PaymentFileValidationIntegrationTest` for the spec scenarios: reused id in a later file → only `DU02` at `PmtInf[2]/PmtInfId`; every reused id reported (`A, C, B` after `A, B`); identical resubmission → only `DU01`; a `DU02`-rejected file registers neither its `MsgId` nor any `PmtInfId`, so a corrected file with the same `MsgId` is accepted; a corrected file after an `AC03` rejection is accepted; scope per submitter; two messages of one container sharing a `PmtInfId` → one accepted, one `DU02`; eight concurrent files sharing `BATCH-1` → exactly one accepted, the rest `DU02`, no rejected `MsgId` registered, no deadlock. Also assert that the logs do not contain the `PmtInfId`. Verify: `mise exec -- ./gradlew fullTest --tests '*PaymentFileValidationIntegrationTest'` fails on the new tests
- [x] 2.3 Add the package-private repository `AcceptedPaymentInformation` in `validation` with `register(submitter, id, fileId)` (`ON CONFLICT DO NOTHING`) and `unregisterFile(fileId)`. Add `AcceptedMessages.unregisterFile(fileId)` (design D3). Verify: compiles and `mise exec -- ./gradlew build` passes
- [x] 2.4 Extend `PaymentFileValidation.record`: after a successful `MsgId` registration, register the `PmtInfId` values in sorted order (design D4). On any conflict, delete this file's rows from both tables and add one `DU02` violation per conflicting block at its path. Update the ABOUTME lines of the touched files. Verify: `mise exec -- ./gradlew fullTest --tests '*PaymentFileValidationIntegrationTest'` passes

## 3. End-to-end (test first)

- [x] 3.1 Write a failing test in `PaymentFileProcessingEndToEndTest`: over SFTP, upload a valid file, then a file with a new `MsgId` reusing its `PmtInfId`. The second report has group status `RJCT` and lists only `DU02`, the first file is `ACCEPTED`, the second `REJECTED`. Verify: `mise exec -- ./gradlew fullTest --tests '*PaymentFileProcessingEndToEndTest'` fails, then passes once tasks 1–2 are in place
- [x] 3.2 Check that the existing end-to-end tests still use a unique `PmtInfId` per file wherever they submit several valid files with fresh `MsgId` values from one submitter (e.g. `validPain001(uniqueMessageId())`). Adjust the fixture helpers to generate a unique `PmtInfId` too. Verify: `mise exec -- ./gradlew fullTest --tests '*EndToEndTest'` passes

## 4. Documentation and full verification

- [x] 4.1 Update `docs/roadmap.md`: remove the `PmtInfId` row from "Remaining duplicate checks" and keep `DU04`. Verify: `git diff docs/roadmap.md` shows only that row removed
- [x] 4.2 Run the unit tests. Verify: `mise exec -- ./gradlew build` passes with clean output
- [x] 4.3 Run all tests, since the change adds a migration and SQL to the acceptance transaction. Verify: `mise exec -- ./gradlew fullTest` passes with clean output
