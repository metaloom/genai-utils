# Gen AI Utils

This project contains utilities to be used for generative ai.

## Maven

```xml
<dependency>
  <groupId>io.metaloom.utils</groupId>
  <artifactId>genai-utils</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```


## Release Process

```bash
# Update maven version to next release
mvn versions:set -DgenerateBackupPoms=false

# Now run tests locally or via GitHub actions
mvn clean package

# Deploy to maven central and auto-close staging repo. 
# Adding the property will trigger the profiles in the parent pom to include gpg,javadoc...
mvn clean deploy -Drelease
```
