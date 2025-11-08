# VajraEdge SDK Publishing Plan

**Created:** November 8, 2025  
**Status:** Planning Phase  
**Target:** Publish `vajraedge-sdk` to Maven Central

## Overview

This document outlines the plan to publish the VajraEdge SDK to Maven Central, making it accessible to developers who want to create custom task plugins and workers without needing to clone the entire repository.

## Current State

### What We Have
- ✅ SDK module (`vajraedge-sdk`) with zero external dependencies
- ✅ Clean separation from core framework
- ✅ Basic Maven publishing configuration in `build.gradle`
- ✅ Apache 2.0 license
- ✅ POM metadata (name, description, URL, developers, SCM)
- ✅ Version: 1.0.0

### What's Missing
- ❌ Maven Central account and setup
- ❌ GPG signing for artifacts
- ❌ Javadoc and sources JAR generation
- ❌ Sonatype OSSRH repository configuration
- ❌ Automated release process
- ❌ Documentation for artifact consumption

## Publishing Options

### Option 1: Maven Central (Sonatype OSSRH) ⭐ RECOMMENDED
**Pros:**
- Industry standard for Java libraries
- Automatically synced to Maven Central
- Trusted by developers worldwide
- Free for open-source projects
- Permanent hosting

**Cons:**
- Initial setup complexity
- Requires GPG signing
- Strict validation rules
- Manual approval for first release

**Requirements:**
1. Sonatype JIRA account
2. Group ID verification (net.vajraedge)
3. GPG key for signing
4. Maven Central credentials

### Option 2: GitHub Packages
**Pros:**
- Easy setup (already have GitHub repo)
- Integrated with GitHub
- Free for public repositories

**Cons:**
- Requires GitHub authentication to download
- Less discoverable than Maven Central
- Not part of standard Maven Central

### Option 3: JitPack
**Pros:**
- Zero setup required
- Auto-publishes from GitHub releases
- No account needed

**Cons:**
- Builds on-demand (slower first download)
- Less control over versioning
- Not as trusted as Maven Central

## Recommended Approach: Maven Central

### Phase 1: Pre-Publishing Setup (2-3 hours)

#### 1.1 Create Sonatype JIRA Account
```bash
# Visit: https://issues.sonatype.org/secure/Signup!default.jspa
# Create account: happysantoo
```

#### 1.2 Verify Domain/Group ID
- Create JIRA ticket to claim `net.vajraedge` group ID
- Prove ownership by:
  - Option A: Add DNS TXT record for vajraedge.net (if you own it)
  - Option B: Use GitHub repository verification (io.github.happysantoo)

**Recommendation:** Use `io.github.happysantoo` as group ID for faster approval:
```gradle
groupId = 'io.github.happysantoo'
artifactId = 'vajraedge-sdk'
```

#### 1.3 Generate GPG Key
```bash
# Generate new GPG key
gpg --gen-key
# Use: happysantoo@gmail.com (or your email)
# Passphrase: <secure password>

# List keys
gpg --list-keys

# Export public key to key server
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# Export secret key for CI/CD
gpg --export-secret-keys <KEY_ID> > secret.gpg
```

#### 1.4 Store Credentials Securely
```bash
# ~/.gradle/gradle.properties
signing.keyId=<last 8 chars of key ID>
signing.password=<gpg passphrase>
signing.secretKeyRingFile=<path to secret.gpg>

ossrhUsername=<sonatype username>
ossrhPassword=<sonatype password>
```

### Phase 2: Update Build Configuration (1 hour)

#### 2.1 Update Root `build.gradle`
```gradle
// Add signing plugin for all subprojects that need publishing
subprojects {
    // ... existing config ...
    
    // Publishing configuration
    ext {
        isPublishable = project.name in ['vajraedge-sdk', 'vajraedge-plugins']
    }
}
```

#### 2.2 Update `vajraedge-sdk/build.gradle`
```gradle
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'signing'
}

// Generate sources JAR
java {
    withSourcesJar()
    withJavadocJar()
}

// Maven publishing
publishing {
    publications {
        maven(MavenPublication) {
            groupId = 'io.github.happysantoo'  // Use GitHub-based group
            artifactId = 'vajraedge-sdk'
            version = project.version
            
            from components.java
            
            pom {
                name = 'VajraEdge SDK'
                description = 'Lightweight SDK for building VajraEdge load testing task plugins and workers'
                url = 'https://github.com/happysantoo/vajraedge'
                
                licenses {
                    license {
                        name = 'The Apache License, Version 2.0'
                        url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                    }
                }
                
                developers {
                    developer {
                        id = 'happysantoo'
                        name = 'Santhosh Kuppusamy'
                        email = 'happysantoo@gmail.com'  // Add email
                    }
                }
                
                scm {
                    connection = 'scm:git:git://github.com/happysantoo/vajraedge.git'
                    developerConnection = 'scm:git:ssh://git@github.com:happysantoo/vajraedge.git'
                    url = 'https://github.com/happysantoo/vajraedge'
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "OSSRH"
            url = version.endsWith('SNAPSHOT') 
                ? "https://s01.oss.sonatype.org/content/repositories/snapshots/" 
                : "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            credentials {
                username = project.findProperty("ossrhUsername") ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

// Signing configuration
signing {
    sign publishing.publications.maven
}

// Only sign if publishing to Maven Central
tasks.withType(Sign) {
    onlyIf { !version.endsWith('SNAPSHOT') }
}
```

#### 2.3 Create `gradle.properties` (Project Level)
```properties
# Project metadata
group=io.github.happysantoo
version=1.0.0

# Signing (will be overridden by ~/.gradle/gradle.properties)
signing.keyId=
signing.password=
signing.secretKeyRingFile=

# OSSRH credentials (will be overridden by ~/.gradle/gradle.properties)
ossrhUsername=
ossrhPassword=
```

### Phase 3: Testing (30 minutes)

#### 3.1 Test Local Publishing
```bash
# Build and publish to local Maven repository
./gradlew :vajraedge-sdk:publishToMavenLocal

# Verify artifacts
ls ~/.m2/repository/io/github/happysantoo/vajraedge-sdk/1.0.0/
# Should see:
# - vajraedge-sdk-1.0.0.jar
# - vajraedge-sdk-1.0.0-sources.jar
# - vajraedge-sdk-1.0.0-javadoc.jar
# - vajraedge-sdk-1.0.0.pom
# - *.asc (signatures)
```

#### 3.2 Test Consumption
Create test project:
```bash
mkdir /tmp/sdk-test
cd /tmp/sdk-test
```

`build.gradle`:
```gradle
plugins {
    id 'java'
}

repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.github.happysantoo:vajraedge-sdk:1.0.0'
}
```

`src/main/java/TestPlugin.java`:
```java
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;

public class TestPlugin implements Task {
    @Override
    public TaskResult execute() throws Exception {
        return new SimpleTaskResult(true, 100);
    }
}
```

```bash
./gradlew build
```

### Phase 4: First Release to Staging (1 hour)

#### 4.1 Publish to OSSRH Staging
```bash
# Clean build
./gradlew clean

# Publish
./gradlew :vajraedge-sdk:publish

# Expected output:
# > Task :vajraedge-sdk:generatePomFileForMavenPublication
# > Task :vajraedge-sdk:publishMavenPublicationToOSSRHRepository
# BUILD SUCCESSFUL
```

#### 4.2 Verify on Sonatype Nexus
1. Login to https://s01.oss.sonatype.org/
2. Click "Staging Repositories"
3. Find your repository (iogitheubhappysantoo-XXXX)
4. Click "Close" to trigger validation
5. Wait for validation to complete
6. Review validation results

#### 4.3 Release to Maven Central
1. Click "Release" in Nexus
2. Wait 10-30 minutes for sync to Maven Central
3. Verify at: https://central.sonatype.com/artifact/io.github.happysantoo/vajraedge-sdk

### Phase 5: Automation (2 hours)

#### 5.1 Create GitHub Actions Workflow
`.github/workflows/publish-sdk.yml`:
```yaml
name: Publish SDK to Maven Central

on:
  release:
    types: [created]
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to publish'
        required: true

jobs:
  publish:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      
      - name: Import GPG key
        run: |
          echo "${{ secrets.GPG_PRIVATE_KEY }}" | base64 -d > secret.gpg
          gpg --import --batch secret.gpg
      
      - name: Publish to Maven Central
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY_ID: ${{ secrets.SIGNING_KEY_ID }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
        run: |
          ./gradlew :vajraedge-sdk:publish
      
      - name: Cleanup
        run: rm -f secret.gpg
```

#### 5.2 Configure GitHub Secrets
```
GPG_PRIVATE_KEY          - Base64 encoded secret key
SIGNING_KEY_ID           - Last 8 chars of GPG key ID
SIGNING_PASSWORD         - GPG passphrase
OSSRH_USERNAME          - Sonatype username
OSSRH_PASSWORD          - Sonatype password
```

### Phase 6: Documentation (1 hour)

#### 6.1 Update README.md
Add Maven dependency section:
```markdown
## Using VajraEdge SDK

### Maven
```xml
<dependency>
    <groupId>io.github.happysantoo</groupId>
    <artifactId>vajraedge-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```gradle
dependencies {
    implementation 'io.github.happysantoo:vajraedge-sdk:1.0.0'
}
```
```

#### 6.2 Create SDK Usage Guide
`vajraedge-sdk/README.md`:
- Quick start guide
- Creating custom tasks
- Using annotations
- Publishing custom plugins

## Timeline Estimate

| Phase | Task | Time | Dependencies |
|-------|------|------|--------------|
| 1 | Sonatype account setup | 30 min | None |
| 1 | Domain verification (GitHub) | 1-2 days | Sonatype approval |
| 1 | GPG key generation | 15 min | None |
| 1 | Credentials setup | 15 min | GPG key |
| 2 | Build configuration | 1 hour | None |
| 3 | Local testing | 30 min | Build config |
| 4 | First staging release | 1 hour | Domain verified |
| 4 | Maven Central sync | 10-30 min | Staging release |
| 5 | CI/CD automation | 2 hours | First release |
| 6 | Documentation | 1 hour | None |

**Total Active Work:** ~6 hours  
**Total Calendar Time:** 2-3 days (waiting for domain verification)

## Release Versioning Strategy

```
1.0.0 - Initial stable release (current SDK)
1.0.1 - Patch releases (bug fixes only)
1.1.0 - Minor releases (new features, backward compatible)
2.0.0 - Major releases (breaking changes)
```

## Post-Publishing

### Monitoring
- Maven Central search: https://central.sonatype.com/
- Download stats: Sonatype provides metrics
- Issue tracking: GitHub Issues for SDK-specific problems

### Release Process
1. Update version in `build.gradle`
2. Create Git tag: `git tag -a sdk-v1.0.1 -m "Release 1.0.1"`
3. Push tag: `git push origin sdk-v1.0.1`
4. Create GitHub Release
5. GitHub Actions automatically publishes to Maven Central

### Communication
- Announce on GitHub Releases
- Update project README with latest version
- Document breaking changes in CHANGELOG.md

## Alternative: Quick Start with JitPack

If you want to get started immediately without Maven Central setup:

### 1. Create GitHub Release
```bash
git tag v1.0.0
git push origin v1.0.0
```

### 2. Users can immediately use:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.happysantoo:vajraedge:v1.0.0'
}
```

**Note:** JitPack builds on-demand, so first download will be slower.

## Recommendation

**For immediate availability:** Use JitPack (5 minutes setup)  
**For production readiness:** Follow Maven Central plan (2-3 days)

The SDK is clean, well-documented, and ready for publishing. I recommend:
1. Start with JitPack for quick testing and feedback
2. Complete Maven Central setup for official 1.0.0 release
3. Automate releases with GitHub Actions

## Next Steps

1. **Decide on approach:** Maven Central vs JitPack vs both
2. **Create Sonatype account** (if Maven Central)
3. **Update build configuration** (I can help with this)
4. **Test local publishing**
5. **Publish first version**

Would you like me to proceed with implementing any of these steps?
