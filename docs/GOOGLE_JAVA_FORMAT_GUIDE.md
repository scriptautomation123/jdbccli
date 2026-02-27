# Google Java Format Guide

## Overview

This project uses Google Java Format via the Spotless Maven Plugin to keep Java 21 code consistent across modules.

---

## Configuration Details

### Plugin: Spotless Maven Plugin v2.44.0

**Location:** `pom.xml` (parent POM module)

**Formatter:** Google Java Format v1.21.0

### Formatting Rules Applied

```xml
<java>
    <googleJavaFormat>
        <version>1.21.0</version>
        <style>GOOGLE</style>
        <reflowLongStrings>true</reflowLongStrings>
    </googleJavaFormat>
    <trimTrailingWhitespace/>
    <endWithNewline/>
    <importOrder>
        <order>java,javax,org,com</order>
        <wildcardsLast>true</wildcardsLast>
    </importOrder>
    <removeUnusedImports/>
</java>
```

---

## What Gets Formatted

- Indentation and line wrapping (Google style)
- Import order: `java`, `javax`, `org`, `com` (wildcards last)
- Trailing whitespace removal and end-of-file newline

---

## Quick Commands

### Apply formatting to all files

```bash
mvn spotless:apply
```

### Check formatting compliance (without modifying)

```bash
mvn spotless:check
```

### Format and skip untracked files (faster for incremental work)

```bash
mvn spotless:apply -DspotlessFollow=true
```

### Format specific module only

```bash
mvn -pl util spotless:apply
mvn -pl database spotless:apply
mvn -pl cli spotless:apply
```

---

## Workflow Integration

- Local: run `mvn spotless:apply` before committing
- CI: run `mvn spotless:check` to enforce formatting

---

## IDE Integration

### VS Code

1. **Install Extension:** [Google Java Format](https://marketplace.visualstudio.com/items?itemName=joseandrade.google-java-format-for-vs-code)

2. **Settings (settings.json):**

```json
{
  "[java]": {
    "editor.defaultFormatter": "joseandrade.google-java-format-for-vs-code",
    "editor.formatOnSave": true
  }
}
```

### IntelliJ IDEA

1. **Install Plugin:**
   - File → Settings → Plugins
   - Search "Google Java Format"
   - Install official plugin

2. **Enable for Project:**
   - File → Settings → Editor → Code Style
   - Scheme: Select "Google Style"
   - Click "Enable Google Java Format"

3. **Format on Save (Optional):**
   - File → Settings → Tools → Actions on Save
   - Enable "Reformat code"

### Eclipse

1. Install: EclipseGoogleStyle from Eclipse Marketplace
2. Configure formatter preference

---

## Troubleshooting

### Issue: `mvn spotless:apply` fails

```bash
mvn clean install && mvn spotless:apply
```

### Issue: Too many files reformatted at once

Format only modified files:

```bash
mvn spotless:apply -DspotlessFollow=true
```

### Issue: IDE formatting differs from Spotless

1. Install Google Java Format extension in your IDE
2. Run `mvn spotless:apply` to resync
3. Confirm IDE style is set to Google (2-space indent, 100-char line limit)

### Issue: Import order keeps changing

The canonical order (defined in `pom.xml`):

```xml
<order>java,javax,org,com</order>
<wildcardsLast>true</wildcardsLast>
```

Order: `java.*` → `javax.*` → `org.*` → `com.*` → wildcards

---

## Additional Resources

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spotless Maven Plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven)
- [Google Java Format on GitHub](https://github.com/google/google-java-format)

---

## Version Information

- **Java Version:** 21
- **Spotless Plugin:** 2.44.0
- **Google Java Format:** 1.21.0
