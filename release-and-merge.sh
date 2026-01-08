#!/bin/bash

# Release and Merge Automation Script
# This script automates the process of creating releases and merging branches
# as documented in RELEASE_AND_MERGE_GUIDE.md

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_info() {
    echo -e "${BLUE}ℹ ${NC}$1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_section() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo ""
}

# Check if git repo
if [ ! -d .git ]; then
    print_error "Not a git repository. Please run this from the repository root."
    exit 1
fi

# Main workflow
print_section "JDBCCLI Release and Merge Workflow"

echo "This script will guide you through:"
echo "  1. Creating a pre-merge release from main branch"
echo "  2. Merging copilot/review-refactor-oo-design into main"
echo "  3. Creating a post-merge release"
echo ""

# Confirm to proceed
read -p "Do you want to proceed? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_warning "Operation cancelled by user."
    exit 0
fi

# Step 1: Create pre-merge release
print_section "Step 1: Create Pre-Merge Release"

print_info "Fetching latest changes from remote..."
git fetch origin

print_info "Checking out main branch..."
git checkout main
git pull origin main

print_info "Current main branch status:"
git log --oneline -5

echo ""
read -p "Enter pre-merge release version (e.g., v1.0.0): " PRE_VERSION

if [ -z "$PRE_VERSION" ]; then
    print_error "Version cannot be empty."
    exit 1
fi

# Check if tag already exists
if git rev-parse "$PRE_VERSION" >/dev/null 2>&1; then
    print_error "Tag $PRE_VERSION already exists."
    exit 1
fi

read -p "Enter release message (or press Enter for default): " PRE_MESSAGE
if [ -z "$PRE_MESSAGE" ]; then
    PRE_MESSAGE="Release $PRE_VERSION - Pre-refactoring stable version"
fi

print_info "Creating tag $PRE_VERSION..."
git tag -a "$PRE_VERSION" -m "$PRE_MESSAGE"

print_info "Pushing tag to remote..."
git push origin "$PRE_VERSION"

print_success "Pre-merge release $PRE_VERSION created successfully!"
echo ""
print_warning "You can now create a GitHub release at:"
echo "https://github.com/scriptautomation123/jdbccli/releases/new?tag=$PRE_VERSION"

# Step 2: Review changes
print_section "Step 2: Review Changes to be Merged"

print_info "Fetching branch to merge..."
git fetch origin copilot/review-refactor-oo-design

print_info "Changes summary:"
git diff --stat main origin/copilot/review-refactor-oo-design | head -20

echo ""
print_info "Commit history that will be merged:"
git log --oneline main..origin/copilot/review-refactor-oo-design

echo ""
read -p "Do you want to see detailed changes? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "Showing key file changes..."
    git diff main origin/copilot/review-refactor-oo-design -- pom.xml | head -50
    echo ""
    print_info "Press Enter to continue..."
    read
fi

# Step 3: Merge
print_section "Step 3: Merge Branch"

echo ""
read -p "Proceed with merging copilot/review-refactor-oo-design into main? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_warning "Merge cancelled by user."
    exit 0
fi

print_info "Merging copilot/review-refactor-oo-design into main..."
if git merge origin/copilot/review-refactor-oo-design --no-ff -m "Merge copilot/review-refactor-oo-design: Java 21 migration and OO design improvements"; then
    print_success "Merge completed successfully!"
else
    print_error "Merge failed with conflicts. Please resolve conflicts manually:"
    echo ""
    echo "  1. Run: git status"
    echo "  2. Resolve conflicts in each file"
    echo "  3. Run: git add <resolved-files>"
    echo "  4. Run: git commit"
    echo "  5. Run this script again from Step 4"
    exit 1
fi

# Step 4: Build and test
print_section "Step 4: Build and Test"

read -p "Do you want to build and test the merged code? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "Running Maven clean install..."
    if mvn clean install; then
        print_success "Build successful!"
    else
        print_error "Build failed. Please fix the issues before proceeding."
        echo ""
        echo "After fixing, you can:"
        echo "  1. Commit the fixes: git add . && git commit -m 'Fix build issues'"
        echo "  2. Continue with pushing: git push origin main"
        exit 1
    fi
    
    print_info "Running tests..."
    if mvn test; then
        print_success "Tests passed!"
    else
        print_error "Tests failed. Please fix the issues before proceeding."
        exit 1
    fi
else
    print_warning "Skipping build and tests. Make sure to test before creating the post-merge release!"
fi

# Step 5: Push merged changes
print_section "Step 5: Push Merged Changes"

read -p "Push merged changes to origin/main? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "Pushing merged changes..."
    git push origin main
    print_success "Merged changes pushed to origin/main!"
else
    print_warning "Changes not pushed. Remember to push before creating the post-merge release."
    exit 0
fi

# Step 6: Create post-merge release
print_section "Step 6: Create Post-Merge Release"

echo ""
read -p "Enter post-merge release version (e.g., v2.0.0): " POST_VERSION

if [ -z "$POST_VERSION" ]; then
    print_error "Version cannot be empty."
    exit 1
fi

# Check if tag already exists
if git rev-parse "$POST_VERSION" >/dev/null 2>&1; then
    print_error "Tag $POST_VERSION already exists."
    exit 1
fi

read -p "Enter release message (or press Enter for default): " POST_MESSAGE
if [ -z "$POST_MESSAGE" ]; then
    POST_MESSAGE="Release $POST_VERSION - Java 21 migration and OO design improvements"
fi

print_info "Creating tag $POST_VERSION..."
git tag -a "$POST_VERSION" -m "$POST_MESSAGE"

print_info "Pushing tag to remote..."
git push origin "$POST_VERSION"

print_success "Post-merge release $POST_VERSION created successfully!"

# Step 7: Cleanup
print_section "Step 7: Cleanup (Optional)"

echo ""
read -p "Do you want to delete the remote branch copilot/review-refactor-oo-design? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_warning "This will permanently delete the remote branch."
    read -p "Are you sure? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Deleting remote branch..."
        git push origin --delete copilot/review-refactor-oo-design || print_warning "Could not delete remote branch. It may have been already deleted or you may not have permissions."
        print_success "Remote branch deleted (if it existed)."
    fi
else
    print_info "Keeping remote branch for reference."
fi

# Summary
print_section "Summary"

print_success "Workflow completed successfully!"
echo ""
echo "Summary:"
echo "  • Pre-merge release: $PRE_VERSION"
echo "  • Post-merge release: $POST_VERSION"
echo "  • Branch merged: copilot/review-refactor-oo-design → main"
echo ""
echo "Next steps:"
echo "  1. Create GitHub releases at:"
echo "     - https://github.com/scriptautomation123/jdbccli/releases/new?tag=$PRE_VERSION"
echo "     - https://github.com/scriptautomation123/jdbccli/releases/new?tag=$POST_VERSION"
echo ""
echo "  2. Update documentation if needed"
echo ""
echo "  3. Notify team members about the new release"
echo ""
print_success "All done! 🎉"
