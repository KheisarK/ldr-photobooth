# Branch strategy

The repository uses three shared branches for this short two-person sprint:

- `main` — stable, integrated, and demo-ready work only.
- `frontend` — owned by the frontend developer; contains React/Vite work.
- `backend` — owned by the backend developer; contains the selected API implementation.

## Join the sprint

After getting collaborator access, fetch the remote branches and check out the branch for your role:

```bash
git fetch origin
git switch --track origin/frontend
# or: git switch --track origin/backend
```

If the branch already exists locally, use `git switch frontend` or `git switch backend` instead. Do not create a second branch with a different name for the same role during the sprint.

## Working agreement

1. Each owner commits only to their branch during the separate build phase.
2. Keep commits small and describe outcomes, for example `feat(frontend): add four-photo capture flow`.
3. Changes to `docs/API.md` must be communicated immediately because both sides depend on it.
4. Pull requests target `main`. The other owner performs a quick contract and smoke-test review.
5. Merge one side, update the remaining branch from `main`, resolve conflicts there, then merge the second side.
6. Never force-push `main`. During the sprint, avoid extra long-lived branches unless a risky experiment truly needs isolation.

Suggested integration order: merge the backend first once its contract is stable, then update and merge the frontend after the end-to-end flow works.
