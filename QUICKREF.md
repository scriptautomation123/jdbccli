# Quick Reference: Release and Merge Workflow

## TL;DR - Quick Commands

### Option 1: Using the Automated Script (Recommended)

```bash
# Run the interactive automation script
./release-and-merge.sh
```

The script will guide you through the entire process interactively.

### Option 2: Manual Commands

```bash
# 1. Create pre-merge release (e.g., v1.0.0)
git checkout main
git pull origin main
git tag -a v1.0.0 -m "Release v1.0.0 - Pre-refactoring stable version"
git push origin v1.0.0

# 2. Merge the refactored branch
git merge origin/copilot/review-refactor-oo-design --no-ff -m "Merge copilot/review-refactor-oo-design: Java 21 migration and OO design improvements"

# 3. Build and test
mvn clean install
mvn test

# 4. Push merged changes
git push origin main

# 5. Create post-merge release (e.g., v2.0.0)
git tag -a v2.0.0 -m "Release v2.0.0 - Java 21 migration and OO design improvements"
git push origin v2.0.0

# 6. (Optional) Clean up the feature branch
git push origin --delete copilot/review-refactor-oo-design
```

## Workflow Steps

### 1. Pre-Merge Release ✓
- **Purpose**: Preserve current stable state
- **Version**: v1.0.0 (or appropriate version)
- **Branch**: main (current state)

### 2. Merge ✓
- **Source**: copilot/review-refactor-oo-design
- **Target**: main
- **Method**: No fast-forward merge (preserves history)

### 3. Test ✓
- Build with Maven
- Run all tests
- Verify functionality

### 4. Post-Merge Release ✓
- **Purpose**: Mark the refactored codebase
- **Version**: v2.0.0 (major version bump for breaking changes)
- **Branch**: main (after merge)

### 5. Cleanup ✓
- Delete feature branch (optional)
- Update documentation
- Create GitHub releases

## Key Files Created

1. **RELEASE_AND_MERGE_GUIDE.md** - Comprehensive documentation
2. **release-and-merge.sh** - Automated workflow script
3. **QUICKREF.md** - This quick reference (you are here)

## Important Notes

### Why Two Releases?

- **v1.0.0 (Pre-merge)**: Safety net / rollback point
- **v2.0.0 (Post-merge)**: Marks the new Java 21 version

### Why v2.0.0?

Major version bump because:
- Java 21 is required (breaking change)
- Significant architectural changes
- API changes in service layer

### Merge Strategy

Using `--no-ff` (no fast-forward) to:
- Preserve branch history
- Create a merge commit
- Make it easier to revert if needed

## What's Changed in the Refactored Branch?

Key improvements in `copilot/review-refactor-oo-design`:

### Breaking Changes
- ✓ **Java 21 required** (was Java 8)
- ✓ Service architecture refactored

### New Features
- ✓ DatabaseConnectionManager for better connection handling
- ✓ Java 21 pattern matching
- ✓ Records for immutability (ConnInfo, VaultConfig)

### Improvements
- ✓ Enhanced error handling (ExceptionUtils)
- ✓ Better code organization
- ✓ Improved type safety

### Files Modified
- 40 files changed
- 2,576 insertions
- 2,369 deletions

## Troubleshooting

### Merge Conflicts?
```bash
# View conflicts
git status

# After resolving manually
git add <resolved-files>
git commit
git push origin main
```

### Failed Tests?
```bash
# Run tests to identify failures
mvn test

# Fix issues, then
git add .
git commit -m "Fix test failures"
git push origin main
```

### Need to Rollback?
```bash
# Option 1: Revert the merge commit
git revert -m 1 <merge-commit-hash>
git push origin main

# Option 2: Reset to v1.0.0 (requires force push)
git reset --hard v1.0.0
git push origin main --force
```

## After Completing the Workflow

### Create GitHub Releases

1. Go to: https://github.com/scriptautomation123/jdbccli/releases
2. Click "Draft a new release"
3. Select tag (v1.0.0 or v2.0.0)
4. Add release notes (see RELEASE_AND_MERGE_GUIDE.md for templates)
5. Publish release

### Update Documentation

- [ ] Update README.md with Java 21 requirements
- [ ] Document new features
- [ ] Update build instructions
- [ ] Update deployment guide

### Notify Team

- [ ] Announce the new release
- [ ] Share migration guide
- [ ] Document breaking changes

## Version Numbering Guide

Follow Semantic Versioning (MAJOR.MINOR.PATCH):

- **MAJOR** (1.x.x → 2.x.x): Breaking changes
  - Java version upgrades ✓
  - API changes ✓
  
- **MINOR** (x.1.x → x.2.x): New features (backward compatible)
  - New commands
  - New functionality
  
- **PATCH** (x.x.1 → x.x.2): Bug fixes (backward compatible)
  - Bug fixes
  - Security patches

## Need Help?

- **Full documentation**: See `RELEASE_AND_MERGE_GUIDE.md`
- **Automated script**: Run `./release-and-merge.sh`
- **Questions**: Contact the repository maintainer

---

**Current Repository State:**
- Main branch: 522359d
- Feature branch: 0c68a0e (4 commits ahead)
- Tags: None (you'll create them)

**Next Action:** Run `./release-and-merge.sh` or follow manual commands above.
