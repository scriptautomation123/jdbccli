# Release and Merge Checklist

Use this checklist to track your progress through the release and merge workflow.

## Pre-Flight Checks

- [ ] Ensure you have write access to the repository
- [ ] Ensure you have permission to create tags and releases
- [ ] Back up any local work in progress
- [ ] Have Maven installed and configured
- [ ] Have Java 21 installed (for post-merge testing)

## Phase 1: Pre-Merge Release

- [ ] Fetch latest changes: `git fetch origin`
- [ ] Checkout main branch: `git checkout main`
- [ ] Pull latest main: `git pull origin main`
- [ ] Review current state: `git log --oneline -5`
- [ ] Decide on version number (suggested: v1.0.0)
- [ ] Create annotated tag: `git tag -a v1.0.0 -m "Release v1.0.0 - Pre-refactoring stable version"`
- [ ] Push tag to remote: `git push origin v1.0.0`
- [ ] Verify tag on GitHub: https://github.com/scriptautomation123/jdbccli/tags
- [ ] Create GitHub release for v1.0.0 (optional but recommended)

## Phase 2: Review Changes

- [ ] Fetch branch to merge: `git fetch origin copilot/review-refactor-oo-design`
- [ ] Review file changes: `git diff --stat main origin/copilot/review-refactor-oo-design`
- [ ] Review commits: `git log --oneline main..origin/copilot/review-refactor-oo-design`
- [ ] Review detailed changes for key files: `git diff main origin/copilot/review-refactor-oo-design -- pom.xml`
- [ ] Understand breaking changes (Java 21 requirement)
- [ ] Note architectural changes (DatabaseConnectionManager, records, etc.)

## Phase 3: Merge

- [ ] Ensure on main branch: `git checkout main`
- [ ] Perform no-fast-forward merge: `git merge origin/copilot/review-refactor-oo-design --no-ff -m "Merge copilot/review-refactor-oo-design: Java 21 migration and OO design improvements"`
- [ ] If conflicts occur:
  - [ ] Check conflicted files: `git status`
  - [ ] Resolve conflicts in each file
  - [ ] Stage resolved files: `git add <file>`
  - [ ] Complete merge: `git commit`
- [ ] Verify merge commit: `git log --oneline -5`

## Phase 4: Build and Test

- [ ] Clean build: `mvn clean install`
- [ ] Run tests: `mvn test`
- [ ] Verify build artifacts created successfully
- [ ] Fix any build or test failures
- [ ] Commit any fixes: `git add . && git commit -m "Fix build/test issues"`
- [ ] Optional: Run the application to verify basic functionality

## Phase 5: Push Changes

- [ ] Review changes to be pushed: `git log origin/main..HEAD`
- [ ] Push merged changes: `git push origin main`
- [ ] Verify push succeeded
- [ ] Check GitHub to confirm merge appears correctly

## Phase 6: Post-Merge Release

- [ ] Ensure on updated main: `git pull origin main`
- [ ] Decide on version number (suggested: v2.0.0 for breaking changes)
- [ ] Create annotated tag: `git tag -a v2.0.0 -m "Release v2.0.0 - Java 21 migration and OO design improvements"`
- [ ] Push tag to remote: `git push origin v2.0.0`
- [ ] Verify tag on GitHub: https://github.com/scriptautomation123/jdbccli/tags
- [ ] Create GitHub release for v2.0.0 with detailed release notes

## Phase 7: Documentation

- [ ] Update README.md with Java 21 requirements (if not already done)
- [ ] Document breaking changes
- [ ] Document new features (DatabaseConnectionManager, records, pattern matching)
- [ ] Update build instructions if needed
- [ ] Update deployment guide if needed
- [ ] Commit documentation updates: `git add . && git commit -m "Update documentation for v2.0.0"`
- [ ] Push documentation: `git push origin main`

## Phase 8: Cleanup (Optional)

- [ ] Delete local feature branch: `git branch -d copilot/review-refactor-oo-design` (if checked out locally)
- [ ] Delete remote feature branch: `git push origin --delete copilot/review-refactor-oo-design`
- [ ] Remove any temporary files or scripts
- [ ] Clean up local repository: `git gc`

## Phase 9: Communication

- [ ] Create GitHub releases with detailed notes:
  - [ ] v1.0.0 release (pre-refactoring baseline)
  - [ ] v2.0.0 release (refactored version with breaking changes)
- [ ] Notify team members about new releases
- [ ] Share migration guide for Java 21 upgrade
- [ ] Document breaking changes for users
- [ ] Update any CI/CD pipelines for Java 21
- [ ] Update any deployment documentation

## Post-Release Verification

- [ ] Verify v1.0.0 tag points to correct commit
- [ ] Verify v2.0.0 tag points to correct commit
- [ ] Verify GitHub releases are published
- [ ] Test that the release artifacts work as expected
- [ ] Verify documentation is accurate and up-to-date
- [ ] Check that no sensitive information was committed

## Rollback Plan (If Needed)

In case issues are discovered after merge:

### Option 1: Revert Merge Commit
- [ ] Find merge commit: `git log --oneline -10`
- [ ] Revert merge: `git revert -m 1 <merge-commit-hash>`
- [ ] Push revert: `git push origin main`

### Option 2: Reset to v1.0.0 (Use with Caution)
- [ ] Backup current state
- [ ] Reset to v1.0.0: `git reset --hard v1.0.0`
- [ ] Force push: `git push origin main --force` (requires permissions)
- [ ] Notify team immediately

## Notes

**Timeline:**
- Started: _______________
- Pre-merge release created: _______________
- Merge completed: _______________
- Tests passed: _______________
- Post-merge release created: _______________
- Completed: _______________

**Versions:**
- Pre-merge release: _______________
- Post-merge release: _______________

**Issues Encountered:**
- ________________________________________________
- ________________________________________________
- ________________________________________________

**Resolutions:**
- ________________________________________________
- ________________________________________________
- ________________________________________________

---

**Quick Commands Reference:**

```bash
# Automated (Recommended)
./release-and-merge.sh

# Manual
git checkout main && git pull origin main
git tag -a v1.0.0 -m "Release v1.0.0 - Pre-refactoring stable version"
git push origin v1.0.0
git merge origin/copilot/review-refactor-oo-design --no-ff
mvn clean install && mvn test
git push origin main
git tag -a v2.0.0 -m "Release v2.0.0 - Java 21 migration and OO design improvements"
git push origin v2.0.0
```

---

**Status:** ☐ Not Started | ⏳ In Progress | ✓ Completed
