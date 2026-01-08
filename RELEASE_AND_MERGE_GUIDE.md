# Release and Merge Workflow Guide

This guide provides step-by-step instructions for creating releases and merging the `copilot/review-refactor-oo-design` branch into `main`.

## Overview

The repository currently has:
- **main branch**: Current stable version (commit 522359d)
- **copilot/review-refactor-oo-design branch**: Contains refactored code with OO design improvements (commit 0c68a0e)
  - 4 commits ahead of main with significant refactoring
  - Migrates to Java 21
  - Converts to records for immutability
  - Adds DatabaseConnectionManager
  - Uses pattern matching improvements

## Recommended Workflow

### Step 1: Create a Pre-Merge Release from Main

Before merging the refactored code, create a release to preserve the current stable state.

```bash
# 1. Checkout main branch
git checkout main
git pull origin main

# 2. Verify the current state
git log --oneline -5
git status

# 3. Create and push a release tag (recommended: v1.0.0 for first release)
git tag -a v1.0.0 -m "Release v1.0.0 - Pre-refactoring stable version"
git push origin v1.0.0
```

**Why create this release first?**
- Preserves the current stable codebase
- Provides a rollback point if needed
- Documents the state before major refactoring
- Allows users to reference the pre-refactored version

### Step 2: Review the Changes

Before merging, review what changes will be included:

```bash
# View file changes summary
git diff --stat main origin/copilot/review-refactor-oo-design

# View detailed changes for specific files
git diff main origin/copilot/review-refactor-oo-design -- pom.xml
git diff main origin/copilot/review-refactor-oo-design -- src/

# Review commit history
git log --oneline main..origin/copilot/review-refactor-oo-design
```

### Step 3: Merge the Refactored Branch

Merge the `copilot/review-refactor-oo-design` branch into `main`:

```bash
# 1. Ensure you're on main and it's up to date
git checkout main
git pull origin main

# 2. Merge the refactored branch
git merge origin/copilot/review-refactor-oo-design --no-ff -m "Merge copilot/review-refactor-oo-design: Java 21 migration and OO design improvements"

# 3. Resolve any merge conflicts if they occur
# (Follow conflict resolution steps if needed)

# 4. Build and test the merged code
mvn clean install
mvn test

# 5. Push the merged changes
git push origin main
```

**Note:** Using `--no-ff` (no fast-forward) creates a merge commit, which:
- Preserves the branch history
- Makes it clear when the refactoring was merged
- Allows easier reverting if needed

### Step 4: Create Post-Merge Release

After successful merge and testing, create a new release:

```bash
# 1. Ensure you're on the updated main branch
git checkout main
git pull origin main

# 2. Create and push the post-merge release tag (recommended: v2.0.0 for major refactoring)
git tag -a v2.0.0 -m "Release v2.0.0 - Java 21 migration and OO design improvements"
git push origin v2.0.0
```

**Why v2.0.0?**
- Major version bump indicates breaking changes (Java 21 requirement)
- Significant architectural changes warrant a major version
- Follows semantic versioning (MAJOR.MINOR.PATCH)

## Version Numbering Guidelines

Following [Semantic Versioning](https://semver.org/):

- **MAJOR** (1.x.x → 2.x.x): Breaking changes
  - Java version upgrades
  - API changes
  - Major refactoring that changes behavior
  
- **MINOR** (x.1.x → x.2.x): New features, backward compatible
  - New commands
  - New functionality
  - Non-breaking enhancements
  
- **PATCH** (x.x.1 → x.x.2): Bug fixes, backward compatible
  - Bug fixes
  - Security patches
  - Documentation updates

## Keeping the Codebase Clean

### Before Merging

1. **Review the branch changes thoroughly**
   ```bash
   git diff main origin/copilot/review-refactor-oo-design
   ```

2. **Ensure all tests pass**
   ```bash
   git checkout copilot/review-refactor-oo-design
   mvn clean test
   ```

3. **Check for unwanted files**
   ```bash
   git status
   # Verify no temporary files, IDE configs, or build artifacts are included
   ```

### After Merging

1. **Clean up the feature branch** (optional)
   ```bash
   # Delete the local branch
   git branch -d copilot/review-refactor-oo-design
   
   # Delete the remote branch (only if no longer needed)
   git push origin --delete copilot/review-refactor-oo-design
   ```

2. **Update documentation**
   - Update README.md with new Java version requirements
   - Document any new features or API changes
   - Update build/deployment instructions if changed

3. **Verify .gitignore is comprehensive**
   ```bash
   # Check current .gitignore
   cat .gitignore
   ```

   Ensure it excludes:
   - Build artifacts (`target/`, `*.jar`, `*.class`)
   - IDE files (`.idea/`, `.vscode/`, `*.iml`)
   - OS files (`.DS_Store`, `Thumbs.db`)
   - Temporary files (`*.tmp`, `*.log`)

### Continuous Cleanup Practices

1. **Regular dependency updates**
   ```bash
   mvn versions:display-dependency-updates
   mvn versions:display-plugin-updates
   ```

2. **Code quality checks**
   ```bash
   mvn clean verify
   ```

3. **Remove unused dependencies**
   ```bash
   mvn dependency:analyze
   ```

## GitHub Release Creation

After pushing tags, create releases on GitHub:

1. Go to https://github.com/scriptautomation123/jdbccli/releases
2. Click "Draft a new release"
3. Select the tag (e.g., v1.0.0 or v2.0.0)
4. Fill in release notes:
   - For v1.0.0: Describe the current stable features
   - For v2.0.0: List the refactoring improvements, Java 21 migration, new features

### Example Release Notes Template

**For v1.0.0 (Pre-Refactoring):**
```markdown
## Release v1.0.0 - Stable Pre-Refactoring Version

This release represents the stable state of the codebase before the Java 21 migration and OO design improvements.

### Features
- JDBC CLI utility for Oracle databases
- SQL and stored procedure execution
- YAML configuration support
- Vault integration for credential management

### Requirements
- Java 8+
- Maven 3.x
```

**For v2.0.0 (Post-Refactoring):**
```markdown
## Release v2.0.0 - Java 21 Migration and OO Design Improvements

This major release includes significant architectural improvements and migration to Java 21.

### Breaking Changes
- **Java 21 Required**: Minimum Java version upgraded from 8 to 21
- Refactored service architecture with improved OO design

### New Features
- DatabaseConnectionManager for better connection handling
- Java 21 pattern matching for cleaner code
- Records for improved immutability (ConnInfo, VaultConfig)

### Improvements
- Enhanced error handling with ExceptionUtils improvements
- Improved code organization and separation of concerns
- Better type safety and null handling

### Migration Guide
- Update Java to version 21 or higher
- Review any custom integrations due to architectural changes
- Test thoroughly before deploying to production

### Requirements
- Java 21+
- Maven 3.x
```

## Troubleshooting

### Merge Conflicts

If merge conflicts occur:

```bash
# 1. View conflicted files
git status

# 2. For each conflicted file, edit and resolve
# Look for conflict markers: <<<<<<<, =======, >>>>>>>

# 3. After resolving, stage the files
git add <resolved-file>

# 4. Complete the merge
git commit -m "Merge copilot/review-refactor-oo-design: Resolved conflicts"

# 5. Push the changes
git push origin main
```

### Failed Tests After Merge

```bash
# 1. Identify failing tests
mvn test

# 2. Fix the issues

# 3. Verify fixes
mvn clean test

# 4. Commit and push
git add .
git commit -m "Fix test failures after merge"
git push origin main
```

### Rollback Strategy

If issues are discovered after merging:

**Option 1: Revert the merge commit**
```bash
# Find the merge commit hash
git log --oneline -10

# Revert the merge
git revert -m 1 <merge-commit-hash>
git push origin main
```

**Option 2: Reset to pre-merge state** (use with caution)
```bash
# Reset to v1.0.0 tag
git reset --hard v1.0.0
git push origin main --force  # Requires force push permissions
```

## Summary

**Recommended Timeline:**
1. ✅ Create v1.0.0 release from current main (before merge)
2. ✅ Merge copilot/review-refactor-oo-design to main
3. ✅ Build and test thoroughly
4. ✅ Create v2.0.0 release from updated main (after merge)
5. ✅ Clean up branches and update documentation

**Best Practices:**
- Always create a pre-merge release tag
- Use semantic versioning
- Test thoroughly after merging
- Document breaking changes
- Keep .gitignore up to date
- Clean up feature branches after successful merge

This workflow ensures a clean, traceable history and provides rollback options if needed.
