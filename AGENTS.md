# AI coding assistant guidelines: Firebase Admin Java SDK

This document defines repository-wide expectations, code styles, and Checkstyle compliance metrics that every autonomous AI coding agent or pair programming assistant must strictly follow when introducing or modifying code in this repository.

---

## 🎨 Code Style & Checkstyle Compliance

This repository enforces standard **Google Java Style** guidelines validated through automated Maven checkstyle executions (`checkstyle.xml`). Any compilation or pull request build will fail if these constraints are violated.

### 1. Strict 100-Character Line Limit
*   **Rule:** No line of code, comments, Javadoc documentation entries, or inline string literal concatenations may exceed **100 characters** under any circumstances.
*   **Remediation:** 
    *   Break long method parameters, array initializations, and logic comparisons onto separate lines.
    *   Wrap long `assertThrows` statements or lambda chains across multiple contiguous lines.
    *   Format long log messages, exception messages, or assertion string explanations using standard string block concatenation broken across separate lines.

### 2. Import Ordering & Grouping
*   **Rule:** Imports must be grouped and arranged alphabetically to avoid validation noise.
*   **Remediation:** 
    1.  Static imports placed first, grouped, and alphabetically arranged.
    2.  Non-static imports grouped alphabetically by package tier.
    3.  Avoid utilizing wildcard (`*`) imports. Every import declaration must be explicit.

### 3. Javadoc Completeness & Formatting
*   **Rule:** Every public class, package-private component interface, constructor, and public method signature must include fully formed Javadoc documentation blocks.
*   **Remediation:** 
    *   Document all arguments via `@param`, explain error flows via `@throws`, and clear return constraints via `@return`.
    *   Wrap documentation description text explicitly so that no single javadoc documentation line passes the 100 characters limit.

### 4. Indentation & Spacing
*   **Rule:** Standard indentation uses exactly **2 spaces** per block indentation level. **4 spaces** are used explicitly for wrapped line continuation indentation.
*   **Remediation:** Never utilize tab characters. Ensure block braces follow the Google K&R opening line placement convention.
