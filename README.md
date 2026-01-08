# JDBC CLI Utility

A command-line utility for executing SQL queries and stored procedures against Oracle databases with support for YAML configuration and HashiCorp Vault integration.

## Features

- SQL query execution with flexible output formats
- Stored procedure execution
- YAML configuration file support
- HashiCorp Vault integration for credential management
- Multiple authentication methods
- Connection pooling and management

## Requirements

### Current Version (main branch)
- Java 8 or higher
- Maven 3.x
- Oracle JDBC driver

### Upcoming Version (after merge)
- **Java 21 or higher** (breaking change)
- Maven 3.x
- Oracle JDBC driver

## Release and Merge Workflow

This repository contains documentation and tools to help with creating releases and merging the refactored branch.

### Quick Start

For the release and merge workflow, you have two options:

#### Option 1: Automated Script (Recommended)
```bash
./release-and-merge.sh
```

#### Option 2: Manual Process
See the documentation files below for step-by-step instructions.

### Documentation Files

- **[QUICKREF.md](QUICKREF.md)** - Quick reference with TL;DR commands
- **[RELEASE_AND_MERGE_GUIDE.md](RELEASE_AND_MERGE_GUIDE.md)** - Comprehensive guide with detailed explanations
- **[CHECKLIST.md](CHECKLIST.md)** - Step-by-step checklist to track your progress
- **[release-and-merge.sh](release-and-merge.sh)** - Automated workflow script

### Workflow Overview

The workflow involves:
1. Creating a pre-merge release from the current `main` branch (e.g., v1.0.0)
2. Merging the `copilot/review-refactor-oo-design` branch into `main`
3. Testing the merged code
4. Creating a post-merge release (e.g., v2.0.0)

This approach ensures you have a stable baseline to fall back to if needed.

## Building the Project

```bash
# Clean and build
mvn clean install

# Run tests
mvn test

# Create executable JAR
mvn package
```

## Usage

```bash
# Execute SQL query
java -jar target/cliutil-1.0.0.jar exec-sql [options]

# Execute stored procedure
java -jar target/cliutil-1.0.0.jar exec-procedure [options]
```

For detailed usage instructions, use the `--help` flag with any command.

## Development

### Project Structure
```
jdbccli/
├── src/main/java/              # Java source code
│   └── com/company/app/service/
├── pom.xml                     # Maven configuration
├── RELEASE_AND_MERGE_GUIDE.md  # Release workflow guide
├── QUICKREF.md                 # Quick reference
├── CHECKLIST.md                # Workflow checklist
└── release-and-merge.sh        # Automation script
```

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Upcoming Changes

The `copilot/review-refactor-oo-design` branch contains significant improvements:

### Breaking Changes
- **Java 21 required** (upgraded from Java 8)
- Service architecture refactored

### New Features
- DatabaseConnectionManager for improved connection handling
- Java 21 pattern matching for cleaner code
- Records for improved immutability (ConnInfo, VaultConfig)

### Improvements
- Enhanced error handling with ExceptionUtils
- Better code organization and separation of concerns
- Improved type safety and null handling

## License

[Add your license information here]

## Support

For questions or issues, please open an issue on GitHub.

---

### Resolving GitHub Email Privacy Issues

When pushing to GitHub, you might encounter the following error:
`remote: error: GH007: Your push would publish a private email address.`

This happens because your GitHub account is configured to keep your email address private, but your local Git configuration is using a personal email address (e.g.,  that matches your account's primary email.

#### How to Fix

To resolve this and prevent future push rejections, you should update your Git configuration to use GitHub's "no-reply" email address.

##### 1. Find Your No-Reply Email
GitHub automatically generates a no-reply email for every user in the format:
`ID+USERNAME@users.noreply.github.com`

For this account, the email is:
******

##### 2. Update Your Git Configuration

**To update only this specific repository (Local):**
Run this command from within the project directory:
```powershell
git config user.email "****************@users.noreply.github.com"
```

**To update for all future projects (Global):**
Run:
```powershell
git config --global user.email "**********@users.noreply.github.com"
```

##### 3. Fix Existing Commits
If you have already made commits with the wrong email and they are failing to push, you need to update those commits before pushing again:

```powershell
git commit --amend --reset-author --no-edit
git push origin main
```

#### Why use the No-Reply address?
*   **Privacy:** Keeps your personal email address hidden from the public commit history.
*   **Reliability:** Prevents GitHub from rejecting your pushes due to privacy protections.
*   **Account Linking:** GitHub still recognizes these commits as yours and associates them with your profile (e.g., showing your avatar and counting towards your contribution graph).