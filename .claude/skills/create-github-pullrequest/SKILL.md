---
name: create-github-pullrequest
description: Open or update a GitHub pull request for the current branch with the gh CLI, filled from .github/pull_request_template.md (ticket, what, risk, test intensively, security deep dive). Use when the user asks to create, open, raise, update or edit a PR, or to refresh a PR description after new commits. Args - optional ticket id, optional "draft".
allowed-tools: Bash(git:*), Bash(gh:*)
---

Open or update a pull request for the current branch. The body follows `.github/pull_request_template.md`.

## Writing rule

Write as briefly as possible. Bullets, not prose. One line per point. No filler, no restating the diff, no marketing words. Write "none" where nothing applies. Remove all template HTML comments from the body.

## Steps

1. **Check state.**
   - `git branch --show-current`. On `main`: stop and ask for a branch name. Never open a PR from `main`.
   - `git status --short`. Uncommitted changes: tell the user and ask whether to commit first. Do not commit without a yes.
   - `gh pr view --json number,url,title,body,state 2>/dev/null` - an open PR exists means **edit mode**, else **create mode**.

2. **Collect facts.**
   - `git fetch origin main` then `git log --format='%h %s%n%b' origin/main..HEAD` and `git diff --stat origin/main...HEAD`.
   - Read the diff (`git diff origin/main...HEAD`) where the stat is not enough to judge risk and security.
   - Changed `openspec/changes/<name>/`: read its `proposal.md` for the "What" section and name the change.

3. **Find the ticket.** Order: skill argument, branch name, commit messages, existing PR body. Pattern: uppercase project key, dash, number (e.g. `SEPA-123`). Not found: ask the user. "none" is valid only for chores.

4. **Fill the template.** Read `.github/pull_request_template.md` and keep its headings and checkboxes.
   - **What**: 1-3 bullets, the why included.
   - **Risk**: tick exactly one level with the template's criteria. Choose the higher level when unsure. Low/Medium: one line "Why:". High: 2-4 bullets - what can break, impact, rollback.
   - **Test intensively**: concrete inputs and paths beyond CI, e.g. "pain.008 with mixed sequence types FRST/RCUR". Derive them from the changed rules, parsers and edge cases in the diff.
   - **Security deep dive**: tick every area the diff touches. One "Where:" line per ticked box with file or class. Tick "None of the above" only when no other box applies.
   - **Checks**: tick only what you ran or saw pass in this session. Do not tick unverified checks. Always tick "AI-assisted code" - this skill writes the PR.
   - **Title**: Conventional Commit style, imperative mood, ticket id prefix: `feat(SEPA-123): reject ...`. Single-commit branch: reuse its subject.

5. **Show the draft.** Print title and body. In create mode, wait for the user's go before step 6. In edit mode, show what changes compared with the current body, then wait.

6. **Publish.** Write the body to a temp file in the scratchpad directory and pass it with `--body-file` (never inline - avoids shell quoting errors).
   - Create mode:
     - `git push -u origin HEAD` if the branch has no upstream or is ahead.
     - `gh pr create --base main --title "<title>" --body-file <file>` (add `--draft` when asked).
   - Edit mode:
     - Push new commits first if the branch is ahead.
     - `gh pr edit <number> --title "<title>" --body-file <file>`.
     - Keep any text a human added to the existing body that the template does not cover.
   - Other edits on request: `gh pr edit <number> --add-reviewer <login>`, `--add-label <label>`, `gh pr ready <number>` to leave draft.

7. **Report.** Print the PR URL from the gh output. Say which checks are ticked and which are not verified.

## Rules

- Never force-push. Never use `--no-verify`.
- Never invent a ticket id, test result or reviewer login.
- Never put payment data from test files (names, IBANs, amounts) in the PR body.
