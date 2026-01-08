# Workflow Diagram

This document provides a visual representation of the release and merge workflow.

## Current Repository State

```
                         main (522359d)
                            │
                            │
         ┌──────────────────┴────────────────────┐
         │                                        │
         │ Current stable code                   │ 
         │ Java 8+                                │
         │                                        │
         └────────────────────────────────────────┘
                            │
                            │
         ┌──────────────────┴────────────────────┐
         │                                        │
         │   copilot/review-refactor-oo-design   │
         │         (0c68a0e)                      │
         │                                        │
         │   • 4 commits ahead of main           │
         │   • Java 21 migration                 │
         │   • OO design improvements            │
         │   • Records & pattern matching        │
         │                                        │
         └────────────────────────────────────────┘
```

## Workflow Process

### Step 1: Create Pre-Merge Release (v1.0.0)

```
         main (522359d)
            │
            ├─────► [Tag v1.0.0] ✓
            │       "Stable baseline"
            │
```

**Purpose:** Create a safety net before making major changes

### Step 2: Merge Feature Branch

```
         main (522359d)
            │
            ├─────► [Tag v1.0.0]
            │
            │  ┌── copilot/review-refactor-oo-design (0c68a0e)
            │  │     ↓
            │  │   • Java 21 migration
            │  │   • DatabaseConnectionManager
            │  │   • Records for immutability
            │  │   • Pattern matching improvements
            │  │     ↓
            ├──┴────► [Merge Commit] --no-ff
            │         "Merge copilot/review-refactor-oo-design"
            │
         main (new merge commit)
```

**Purpose:** Integrate refactored code into main branch

### Step 3: Create Post-Merge Release (v2.0.0)

```
         main (new merge commit)
            │
            ├─────► [Tag v2.0.0] ✓
            │       "Java 21 + Refactoring"
            │
```

**Purpose:** Mark the new version with breaking changes

## Complete Timeline

```
                                  WORKFLOW TIMELINE
                                  
Phase 1:          Phase 2:        Phase 3:         Phase 4:
Pre-Merge         Merge           Test & Push      Post-Merge
Release                                             Release
    │                │                │                │
    ▼                ▼                ▼                ▼
    
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│         │    │         │    │         │    │         │
│ Create  │ ──→│ Merge   │ ──→│ Build & │ ──→│ Create  │
│ v1.0.0  │    │ Feature │    │  Test   │    │ v2.0.0  │
│  Tag    │    │ Branch  │    │         │    │  Tag    │
│         │    │         │    │         │    │         │
└─────────┘    └─────────┘    └─────────┘    └─────────┘
    │                │                │                │
    ▼                ▼                ▼                ▼
    
Stable         Integrated      Verified         Released
Baseline       Changes         Working          New Version
```

## Version History After Workflow

```
Timeline (newest to oldest):

v2.0.0  ────────────────────► [Current: Java 21 + Refactoring]
   │                           • DatabaseConnectionManager
   │                           • Records & pattern matching
   │                           • Enhanced error handling
   │
   │ Merge Commit
   │
v1.0.0  ────────────────────► [Baseline: Pre-refactoring]
   │                           • Java 8+
   │                           • Original architecture
   │                           • Stable reference point
   │
   ▼
Previous commits...
```

## Branching Strategy

### Before Workflow:
```
main ──────────────────────────► (522359d)
       
       copilot/review-refactor-oo-design
       └──────────────────────► (0c68a0e)
```

### After Workflow:
```
main ──────┬──────────────────► (merged, v2.0.0)
           │                     
           ├─► v1.0.0 (baseline tag)
           │
           └─► v2.0.0 (current tag)

copilot/review-refactor-oo-design (optional: delete)
```

## Rollback Strategy

If issues are found after merge:

### Option 1: Revert Merge (Recommended)
```
main ──────────────[Merge]──────► (v2.0.0)
                      │
                      ▼
                   [Revert]
                      │
                      ▼
main ──────────────[Back to v1.0.0 state]
```

### Option 2: Hard Reset (Use with Caution)
```
main ──────────────[Merge]──────► (v2.0.0)
                      │
                      │ git reset --hard v1.0.0
                      ▼
main ◄─────────────[Force Push]
```

## GitHub Release Strategy

```
┌────────────────────────────────────────────────┐
│           GitHub Releases Page                 │
├────────────────────────────────────────────────┤
│                                                │
│  v2.0.0 - Latest Release ★                     │
│  └── Java 21 Migration and OO Improvements    │
│      • Breaking changes documented            │
│      • Migration guide included               │
│                                                │
│  v1.0.0 - Stable Baseline                      │
│  └── Pre-refactoring stable version           │
│      • Rollback reference                     │
│      • Legacy support                         │
│                                                │
└────────────────────────────────────────────────┘
```

## Decision Tree

```
                     START
                       │
                       ▼
           ┌───────────────────────┐
           │  Ready to release?    │
           └───────────────────────┘
                  │         │
              Yes │         │ No
                  │         └──► Wait / Prepare
                  ▼
           ┌───────────────────────┐
           │ Create v1.0.0 from    │
           │    current main       │
           └───────────────────────┘
                       │
                       ▼
           ┌───────────────────────┐
           │ Review feature branch │
           │      changes          │
           └───────────────────────┘
                       │
                       ▼
           ┌───────────────────────┐
           │ Changes acceptable?   │
           └───────────────────────┘
                  │         │
              Yes │         │ No
                  │         └──► Revise / Fix
                  ▼
           ┌───────────────────────┐
           │  Merge to main        │
           └───────────────────────┘
                       │
                       ▼
           ┌───────────────────────┐
           │  Build & Test         │
           └───────────────────────┘
                  │         │
              Pass│         │ Fail
                  │         └──► Fix Issues
                  ▼
           ┌───────────────────────┐
           │  Push to remote       │
           └───────────────────────┘
                       │
                       ▼
           ┌───────────────────────┐
           │  Create v2.0.0        │
           └───────────────────────┘
                       │
                       ▼
                   SUCCESS ✓
```

## Key Commands Flow

```
┌──────────────────────────────────────────────────────────┐
│                   Command Sequence                        │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  1. git checkout main                                    │
│     └─► Switch to main branch                           │
│                                                           │
│  2. git tag -a v1.0.0 -m "..."                          │
│     └─► Create baseline tag                             │
│                                                           │
│  3. git push origin v1.0.0                              │
│     └─► Push baseline tag                               │
│                                                           │
│  4. git merge origin/copilot/review-refactor-oo-design  │
│     └─► Merge feature branch (--no-ff)                  │
│                                                           │
│  5. mvn clean install && mvn test                       │
│     └─► Build and test                                  │
│                                                           │
│  6. git push origin main                                │
│     └─► Push merged changes                             │
│                                                           │
│  7. git tag -a v2.0.0 -m "..."                          │
│     └─► Create release tag                              │
│                                                           │
│  8. git push origin v2.0.0                              │
│     └─► Push release tag                                │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

## Semantic Versioning Decision

```
        Change Type?
             │
     ┌───────┼───────┐
     │       │       │
Breaking  Feature  Bugfix
Change    Added    Only
     │       │       │
     ▼       ▼       ▼
   MAJOR   MINOR   PATCH
   (2.0)   (1.1)   (1.0.1)
     
Example in this workflow:
Java 21 requirement = BREAKING CHANGE = v2.0.0
```

## Summary

This workflow ensures:
- ✓ Safe release process with rollback option
- ✓ Clear version history
- ✓ Preserved branch history (no-fast-forward merge)
- ✓ Proper semantic versioning
- ✓ Clean codebase maintenance

---

**Quick Navigation:**
- [Comprehensive Guide](RELEASE_AND_MERGE_GUIDE.md) - Detailed documentation
- [Quick Reference](QUICKREF.md) - Fast command lookup
- [Checklist](CHECKLIST.md) - Track your progress
- [README](README.md) - Project overview
