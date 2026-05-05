# AI Coding Agent Guidelines: Firebase Phone Number Verification

This document outlines crucial security constraints, robustness patterns, and layout formatting styles that must be followed when modifying or enhancing the Firebase Phone Number Verification component in this SDK.

---

## 🔒 Critical Security Rules

### 1. Project-Specific Issuer Validation
When verifying Phone Number Verification JWT tokens, **always explicitly validate the project ID in the issuer (`iss`) claim**. 
- Public keys are fetched from a global Google JWKS endpoint (`https://fpnv.googleapis.com/v1beta/jwks`) which returns signature keys for all valid tokens across all projects.
- Failing to check that the token's issuer matches the application's specific project ID (`https://fpnv.googleapis.com/projects/[PROJECT_ID]`) will lead to a **cross-project token reuse vulnerability**.

Ensure the following check remains intact during claim validation:
```java
String expectedIssuer = "https://fpnv.googleapis.com/projects/" + this.projectId;
if (!expectedIssuer.equals(issuer)) {
  throw new FirebasePhoneNumberVerificationException(
      FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
      "Firebase Phone Number Verification token has an incorrect 'iss' (issuer) claim."
  );
}
```

---

## 🛡️ Robustness & Claim Type Safeguards

### 2. Heterogeneous Map Types for Timestamps
Do not cast numeric token claims (like `exp` or `iat`) directly to `java.util.Date` objects. When maps are instantiated from raw JSON objects or direct values, claims may be returned as numbers (`Long` or `Integer`). 
Always safely handle both types:
```java
Object exp = claims.get("exp");
if (exp instanceof java.util.Date) {
  return ((java.util.Date) exp).getTime() / 1000L;
}
return exp instanceof Number ? ((Number) exp).longValue() : 0L;
```

---

## 🎨 Code Style & Checkstyle Compliance

### 3. Strict 100-Character Line Limit
This repository enforces standard Google Java Checkstyle constraints via Maven validation. **No line of code, Javadoc comment, or string concatenation string block may exceed 100 characters.**
- When adding long exception text or assertion descriptions, break them up into contiguous segments or utilize line wraps.
- Wrap deep generic types and anonymous lambda expressions inside test statements onto separate lines to stay well within the 100-character bounds.
