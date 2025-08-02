# BoxLang AWS Lambda Runtime - AI Development Guide

## Project Overview

This is a **BoxLang AWS Lambda Runtime** that enables running BoxLang code in AWS Lambda functions. The runtime acts as a bridge between AWS Lambda's Java 21 runtime and BoxLang's dynamic language features.

### Core Architecture

- **Entry Point**: `ortus.boxlang.runtime.aws.LambdaRunner` - AWS Lambda RequestHandler
- **Convention**: Lambda functions execute `Lambda.bx` files via the `run()` method
- **Response Pattern**: Functions can return data directly OR populate a `response` struct with `statusCode`, `headers`, and `body`
- **BoxLang Integration**: Uses BoxLang runtime for dynamic compilation and execution

## Essential Development Patterns

### Lambda Function Structure

BoxLang Lambda functions follow this pattern in `Lambda.bx`:
```boxlang
class{
    function run( event, context, response ){
        // Option 1: Use response struct
        response.statusCode = 200;
        response.headers = { "Content-Type": "application/json" };
        response.body = { "message": "Hello World" };

        // Option 2: Just return data (auto-populated in response.body)
        return "Hello World";
    }
}
```

### Environment Variables

- `BOXLANG_LAMBDA_CLASS`: Override default `Lambda.bx` file path
- `BOXLANG_LAMBDA_DEBUGMODE`: Enable debug logging
- `BOXLANG_LAMBDA_CONFIG`: Custom BoxLang config path (defaults to `/var/task/boxlang.json`)
- `LAMBDA_TASK_ROOT`: Lambda deployment root (defaults to `/var/task`)

### Build System (Gradle)

**Key Commands:**
- `./gradlew shadowJar` - Build fat JAR with all dependencies
- `./gradlew buildMainZip` - Create deployable Lambda zip with `Lambda.bx` + runtime
- `./gradlew buildTestZip` - Create test package

**Important Build Facts:**
- Uses shadow plugin for fat JARs
- Automatically handles BoxLang dependencies (local or downloaded)
- Branch-aware versioning: `development` branch appends `-snapshot`
- Generates checksums (SHA-256, MD5) for all artifacts

### Local Development & Testing

**AWS SAM Integration:**
```bash
# Build first
./gradlew shadowJar buildMainZip

# Test locally with sample events
sam local invoke bxFunction --event=workbench/sampleEvents/api.json
sam local invoke bxFunction --event=workbench/sampleEvents/event.json

# Debug mode
sam local invoke bxFunction --event=workbench/sampleEvents/api.json --debug
```

**File Structure for Testing:**
- `workbench/sampleEvents/` - Sample Lambda event payloads
- `workbench/template.yml` - SAM template for local testing
- `src/test/resources/Lambda.bx` - Test Lambda function

### Dependency Management

**Local Development Setup:**
- If `../boxlang/build/libs/boxlang-{version}.jar` exists, uses local BoxLang build
- Otherwise downloads dependencies to `src/test/resources/libs/`
- Web support included via `boxlang-web-support` dependency

**Key Dependencies:**
- `com.amazonaws:aws-lambda-java-core` - AWS Lambda integration
- `org.slf4j:slf4j-nop` - Logging (no-op for Lambda)
- BoxLang runtime and web support JARs

### Testing Patterns

**Unit Tests Location:** `src/test/java/ortus/boxlang/runtime/aws/`
- Test Lambda functions in `src/test/resources/Lambda.bx`
- Mock AWS Context using custom `TestContext` implementations
- Use Google Truth assertions: `assertThat(...)`

### AWS Deployment Structure

**Required ZIP Structure:**
```
Lambda.bx                    # Your BoxLang function
lib/
  boxlang-aws-lambda-{version}.jar  # Runtime JAR
```

**SAM Template Pattern:**
- Handler: `ortus.boxlang.runtime.aws.LambdaRunner::handleRequest`
- Runtime: `java21`
- CodeUri points to `build/distributions/` zip file

### BoxLang-Specific Considerations

**Runtime Initialization:**
- Static initialization of BoxLang runtime for performance
- Stateless execution model (uses system temp directory)
- Dynamic class loading and compilation of `.bx` files

**Method Resolution:**
- Default method: `run`
- Custom method via `x-bx-function` header
- Event, context, and response parameters automatically injected

## Common Workflows

1. **New Lambda Function**: Create/modify `src/main/resources/Lambda.bx`
2. **Local Testing**: Use SAM CLI with sample events in `workbench/sampleEvents/`
3. **Building**: Always run `shadowJar` before `buildMainZip` for deployment
4. **Debugging**: Set `BOXLANG_LAMBDA_DEBUGMODE=true` environment variable

## File Locations to Remember

- Main runtime: `src/main/java/ortus/boxlang/runtime/aws/LambdaRunner.java`
- Default Lambda: `src/main/resources/Lambda.bx`
- Build config: `build.gradle` (shadow plugin configuration)
- Version info: `gradle.properties`
- Sample events: `workbench/sampleEvents/`
