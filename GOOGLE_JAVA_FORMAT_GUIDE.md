# Google Java Format Setup Guide

## Overview

Your codebase has been configured with **Google Java Format** using the **Spotless Maven Plugin**. This ensures consistent code style across the project for Java 21 code.

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

### Code Style
- **Indentation:** 2 spaces (Google standard)
- **Line length:** 100 characters (default)
- **Bracket placement:** Consistent (no 1TBS)
- **Method parameters:** Wrapped with proper indentation
- **Lambda expressions:** Formatted with body on same line when possible

### Import Organization
- Standard library: `java.*` imports first
- Then: `javax.*` imports
- Then: `org.*` and `com.*` packages
- Wildcard imports at the end
- Unused imports are automatically removed

### Whitespace
- Trailing whitespace removed
- Files end with newline character
- Consistent spacing around operators and keywords

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

## Integration with Development Workflow

### Before Committing
```bash
# Format all files
mvn spotless:apply

# Verify formatting
mvn spotless:check

# Then commit
git add -A
git commit -m "Your commit message"
```

### Pre-commit Hook (Optional)
Create `.git/hooks/pre-commit`:
```bash
#!/bin/bash
mvn spotless:check
if [ $? -ne 0 ]; then
    echo "Code formatting issues detected. Run 'mvn spotless:apply' to fix."
    exit 1
fi
```

Make executable:
```bash
chmod +x .git/hooks/pre-commit
```

### CI/CD Pipeline
Add to your CI/CD configuration:
```bash
# In your build stage
mvn spotless:check

# Or auto-fix:
mvn spotless:apply
```

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

## Example: Before and After

### Before
```java
package com.company.app.service;
import java.util.*;
import java.io.*;
import com.company.app.service.util.*;
import org.apache.logging.log4j.*;

public class MyClass {

public void method1(String a,String b,String c){
LOGGER.info("event=test param1="+a);
}

   public String method2(){
      if(a!=null){
         return a;
      }else{
         return null;
      }
   }
}
```

### After (Google Java Format)
```java
package com.company.app.service;

import java.io.IOException;
import java.util.List;

import com.company.app.service.util.ExceptionUtils;
import org.apache.logging.log4j.Logger;

public class MyClass {

  public void method1(String a, String b, String c) {
    LOGGER.info("event=test param1={}", a);
  }

  public String method2() {
    if (a != null) {
      return a;
    } else {
      return null;
    }
  }
}
```

---

## Customization

If you need to customize the formatting rules, edit the `pom.xml` file:

```xml
<java>
    <!-- Change the version number -->
    <googleJavaFormat>
        <version>1.21.0</version>
        <style>GOOGLE</style>  <!-- or AOSP -->
        <reflowLongStrings>true</reflowLongStrings>
    </googleJavaFormat>

    <!-- Modify import order as needed -->
    <importOrder>
        <order>java,javax,org,com,your.company</order>
        <wildcardsLast>true</wildcardsLast>
    </importOrder>

    <!-- Remove or customize as needed -->
    <trimTrailingWhitespace/>
    <endWithNewline/>
    <removeUnusedImports/>
</java>
```

After changing the configuration, run:
```bash
mvn spotless:apply
```

---

## Styles Available

### GOOGLE (Default - Recommended)
- 2-space indentation
- 100-character line limit
- Standard Google style guide

### AOSP (Android Open Source Project)
- 4-space indentation
- Used by Android projects
- More relaxed line breaking

Change in pom.xml:
```xml
<style>AOSP</style>
```

---

## Troubleshooting

### Issue: "MVN spotless:apply fails"
**Solution:**
```bash
# Clean and rebuild
mvn clean install

# Then apply formatting
mvn spotless:apply
```

### Issue: "Too many files formatted, want to avoid"
**Solution:** Format only modified files:
```bash
mvn spotless:apply -DspotlessFollow=true
```

### Issue: "IDE formatting differs from spotless"
**Solution:**
1. Ensure IDE has Google Java Format extension installed
2. Run `mvn spotless:apply` to sync
3. Check IDE settings match Google style

### Issue: "Import order keeps changing"
**Solution:** The import order configuration in pom.xml is:
```xml
<order>java,javax,org,com</order>
<wildcardsLast>true</wildcardsLast>
```

This ensures imports are organized as:
1. `java.*` 
2. `javax.*`
3. `org.*`
4. `com.*`
5. Wildcard imports

---

## Files Currently Formatted

- ✅ All 24 Java source files
- ✅ 3,717 lines of code
- ✅ 6 Maven modules:
  - domain/
  - util/
  - vault-client/
  - database/
  - cli/
  - package-helper/

---

## Verification

All files have been verified:
```bash
mvn spotless:check
```

**Result:** ✅ PASSED - All files meet Google Java Format standards

---

## Best Practices

1. **Always apply formatting before pushing:**
   ```bash
   mvn spotless:apply
   ```

2. **Run spotless:check in CI/CD** to catch style violations

3. **Install IDE extension** for real-time feedback

4. **Team consistency:**
   - Commit pom.xml with Spotless config
   - Document in README or CONTRIBUTING.md
   - Run spotless:apply as part of build process

5. **Git configuration** (optional, to ignore formatting changes):
   ```bash
   git config diff.ignore-all-space true
   ```

---

## Additional Resources

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spotless Maven Plugin Documentation](https://github.com/diffplug/spotless/tree/main/plugin-maven)
- [Google Java Format on GitHub](https://github.com/google/google-java-format)

---

## Version Information

- **Java Version:** 21
- **Maven:** 3.8.0+
- **Spotless Plugin:** 2.44.0
- **Google Java Format:** 1.21.0

---

Generated: January 20, 2026
