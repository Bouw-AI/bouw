# Agent Workflow

Codex agents working in this repository should always create a pull request when they complete a feature.
Any UI change must include at least one current screenshot of the changed state in the pull request.
When a task updates specific screens, agents should capture screenshots of each specific updated screen after the work is complete, not just a generic app shell.

## Screenshot Login

Use the seeded screenshot test account when verifying authenticated UI flows and capturing screenshots:

- Username: `screenshot-test`
- Password: `hugin-screenshot`

Agents should log in with that account before taking post-change UI screenshots unless the task specifically requires a different user state.

Agents working in this repository should follow this workflow:

1. `git pull`
2. Create a branch for the task.
3. Complete the feature and test it.
4. Create a pull request in `ready for review` state.
5. Wait 2 minutes for checks to finish.
6. Read the most recent pull request review comment.
7. Address major concerns.
8. Push changes.
9. Iterate until all checks are green and there are no critical-level concerns in review.
10. Merge once checks complete successfully.
