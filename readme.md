# ⚡︎ BoxLang AWS Lambda Runtime

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

## 🚀 Welcome to the BoxLang AWS Lambda Runtime

This repository contains the **core AWS Lambda Runtime** for the BoxLang language. This runtime acts as a bridge between AWS Lambda's Java 21 runtime and BoxLang's dynamic language features, enabling BoxLang code execution in serverless environments.

> 💡 **For creating Lambda projects**: Use our [BoxLang AWS Lambda Template](https://github.com/ortus-boxlang/bx-aws-lambda-template) to quickly bootstrap new serverless applications.

## 🏗️ Architecture Overview

The runtime consists of:

- **`ortus.boxlang.runtime.aws.LambdaRunner`** - Main AWS Lambda RequestHandler
- **Dynamic Class Compilation** - Compiles `.bx` files on-demand with intelligent caching
- **Convention-based Execution** - Executes `Lambda.bx` files via the `run()` method
- **Flexible Response Handling** - Supports both direct returns and response struct population

### 🔄 Runtime Flow

1. **Static Initialization** - BoxLang runtime loads once per Lambda container
2. **Class Compilation** - `.bx` files are compiled and cached for performance
3. **Method Resolution** - Discovers target method via convention or `x-bx-function` header
4. **Application Lifecycle** - Full Application.bx lifecycle with onRequestStart/End
5. **Response Marshalling** - Converts BoxLang responses to Lambda-compatible JSON

## 🛠️ Development Setup

### Prerequisites

- **Java 21+** - Required for BoxLang runtime
- **AWS CLI** - For deployment and testing
- **AWS SAM CLI** - For local development and testing
- **Docker** - Required by SAM for local Lambda emulation

### Local Development

```bash
# Clone the runtime repository
git clone https://github.com/ortus-boxlang/boxlang-aws-lambda.git
cd boxlang-aws-lambda

# Build the runtime
./gradlew build shadowJar

# Create deployment packages
./gradlew buildMainZip buildTestZip

# Run tests
./gradlew test

# Local testing with SAM
sam local invoke bxFunction --event=workbench/sampleEvents/api.json
```

### 🏃‍♂️ Performance Testing

Run comprehensive performance benchmarks:

```bash
# Execute performance test suite
./workbench/performance-test.sh

# Quick validation test
./workbench/simple-test.sh
```

## 🧩 Core Components

### LambdaRunner.java

The main entry point implementing AWS Lambda's `RequestHandler<Map<String, Object>, Object>`:

- **Static Initialization** - BoxLang runtime loads once per container
- **Class Caching** - Compiled BoxLang classes cached via `ConcurrentHashMap`
- **Performance Metrics** - Debug timing for compilation and execution
- **Connection Pooling** - Configurable connection pool sizes

### Environment Variables

Runtime behavior is controlled via environment variables:

- `BOXLANG_LAMBDA_CLASS` - Override default `Lambda.bx` class path
- `BOXLANG_LAMBDA_DEBUGMODE` - Enable debug logging and performance metrics
- `BOXLANG_LAMBDA_CONFIG` - Custom BoxLang configuration path (default: `/var/task/boxlang.json`)
- `BOXLANG_LAMBDA_CONNECTION_POOL_SIZE` - Connection pool size (default: 2)
- `LAMBDA_TASK_ROOT` - Lambda deployment root (default: `/var/task`)

### Build System (Gradle)

Key build tasks:

- `build` - Builds, tests and packages
- `shadowJar` - Creates fat JAR with all dependencies
- `buildMainZip` - Packages deployable Lambda runtime + sample
- `buildTestZip` - Creates test package for validation
- `spotlessApply` - Code formatting and linting

## 🔬 Testing Infrastructure

### Sample Events

The `workbench/sampleEvents/` directory contains test payloads:

- `api.json` - API Gateway integration event
- `event.json` - Simple Lambda event
- `health.json` - Health check event
- `large-payload.json` - Large payload stress test

### Unit Tests

Tests are located in `src/test/java/ortus/boxlang/runtime/aws/`:

- **LambdaRunnerTest** - Core runtime functionality
- **Performance Tests** - Class caching and optimization validation
- **Integration Tests** - Full Lambda lifecycle testing

### Local Testing with SAM

```bash
# Test API Gateway event
sam local invoke bxFunction --event=workbench/sampleEvents/api.json

# Test with debug logging
sam local invoke bxFunction --event=workbench/sampleEvents/api.json --debug

# Performance benchmarking
sam local invoke bxFunction --event=workbench/sampleEvents/large-payload.json
```

## ⚡ Performance Optimizations

### Class Compilation Caching

The runtime implements intelligent caching to avoid recompilation:

```java
private static final ConcurrentHashMap<String, Class<?>> classCache = new ConcurrentHashMap<>();

private Class<?> getOrCompileLambda(String lambdaClassPath) {
    return classCache.computeIfAbsent(lambdaClassPath, path -> {
        // Compilation logic here
    });
}
```

### Performance Metrics

When `BOXLANG_LAMBDA_DEBUGMODE=true`, the runtime logs:

- Compilation time vs cache retrieval
- Method execution duration
- Memory usage patterns
- Connection pool statistics

### Best Practices for Contributors

- **Minimize Cold Start Impact** - Keep static initialization lightweight
- **Cache Aggressively** - Store expensive computations in static variables
- **Profile Memory Usage** - Monitor CloudWatch logs for memory patterns
- **Early Validation** - Fail fast on invalid inputs to reduce execution time

## 🏗️ Build & Deployment

### Creating Runtime Distributions

```bash
# Build all artifacts
./gradlew clean build shadowJar buildMainZip buildTestZip

# Artifacts created in build/distributions/:
# - boxlang-aws-lambda-{version}-all.jar (fat JAR)
# - boxlang-aws-lambda-{version}.zip (deployable package)
# - boxlang-aws-lambda-test-{version}.zip (test package)
```

### Versioning

- **Development Branch** - Versions append `-snapshot`
- **Release Branches** - Clean semantic versions
- **Checksums** - SHA-256 and MD5 generated for all artifacts

### Dependencies

The runtime automatically handles BoxLang dependencies:

- **Local Development** - Uses `../boxlang/build/libs/boxlang-*.jar` if available
- **CI/CD** - Downloads dependencies to `src/test/resources/libs/`
- **Web Support** - Includes `boxlang-web-support` for HTTP utilities

## 🧪 Contributing Guidelines

### Development Workflow

1. **Fork & Clone** - Create your development environment
2. **Feature Branch** - Create branch from `development`
3. **Build & Test** - Ensure all tests pass locally
4. **Performance Test** - Run performance benchmarks
5. **Code Quality** - Run `./gradlew spotlessApply` for formatting
6. **Pull Request** - Target the `development` branch

### Code Standards

- **Java 21** - Use modern Java features appropriately
- **BoxLang Integration** - Follow BoxLang runtime patterns
- **Performance First** - Consider Lambda cold start implications
- **Documentation** - Update relevant docs and comments
- **Testing** - Add tests for new functionality

### Debugging Runtime Issues

1. **Enable Debug Mode** - Set `BOXLANG_LAMBDA_DEBUGMODE=true`
2. **Check CloudWatch Logs** - Look for compilation/execution metrics
3. **Local SAM Testing** - Use `sam local invoke --debug`
4. **Unit Test Isolation** - Create focused tests for specific issues

## 📚 Additional Resources

### BoxLang Documentation

- **Main Documentation** - [boxlang.ortusbooks.com](https://boxlang.ortusbooks.com)
- **AWS Lambda Guide** - [BoxLang Lambda Documentation](https://boxlang.ortusbooks.com/getting-started/running-boxlang/aws-lambda)
- **IDE Tooling** - [Development Tools](https://boxlang.ortusbooks.com/getting-started/ide-tooling)

### Related Projects

- **Lambda Template** - [bx-aws-lambda-template](https://github.com/ortus-boxlang/bx-aws-lambda-template)
- **BoxLang Core** - [boxlang](https://github.com/ortus-boxlang/boxlang)
- **Web Support** - [boxlang-web-support](https://github.com/ortus-boxlang/boxlang-web-support)

---

## 📖 BoxLang Information

BoxLang is a modern, dynamic JVM language that can be deployed on multiple runtimes: operating system (Windows/Mac/*nix/Embedded), web server, lambda, iOS, android, web assembly, etc.

**BoxLang Features:**

- Be a rapid application development (RAD) scripting language and middleware.
- Unstagnate the dynamic language ecosystem in Java.
- Be dynamic, modular, lightweight, and fast.
- Be 100% interoperable with Java.
- Be modern, functional, and fluent (Think mixing CFML, Node, Kotlin, Java, and Clojure)
- Be able to support multiple runtimes and deployment targets:
  - Native OS Binaries (CLI Tooling, compilers, etc.)
  - MiniServer
  - Servlet Containers - CommandBox/Tomcat/Jetty/JBoss
  - JSR223 Scripting Engines
  - AWS Lambda
  - Microsoft Azure Functions (Coming Soon)
  - Android/iOS Devices (Coming Soon)
  - Web assembly (Coming Soon)
- Compile down to Java ByteCode
- Allow backward compatibility with the existing ColdFusion/CFML language.
- Great IDE, Debugger and Tooling: https://boxlang.ortusbooks.com/getting-started/ide-tooling
- Scripting (Any OS and Shebang) and REPL capabilities

## 💰 Professional Support & Services

Get professional support, training, and custom development services for your BoxLang applications:

- Professional Support and Priority Queuing
- Remote Assistance and Troubleshooting

You can find our docs here: https://boxlang.ortusbooks.com/

## License

Apache License, Version 2.0.

## Open-Source & Professional Support

This project is a professional open source project and is available as FREE and open source to use.  Ortus Solutions, Corp provides commercial support, training and commercial subscriptions which include the following:

- Professional Support and Priority Queuing
- Remote Assistance and Troubleshooting
- New Feature Requests and Custom Development
- Custom SLAs
- Application Modernization and Migration Services
- Performance Audits
- Enterprise Modules and Integrations
- Much More

<p>&nbsp;</p>

<blockquote>
"We ❤️ Open Source and BoxLang" - Luis Majano
</blockquote>

---

## ⭐ Star Us

Please star us if this runtime helps you build amazing serverless applications with BoxLang!

---

## 🙏 Sponsors

A huge thanks to our sponsors who help us continue developing BoxLang and its ecosystem:

<div align="center">
  <a href="https://www.ortussolutions.com"><img src="https://www.ortussolutions.com/__media/ortus-medium.png" alt="Ortus Solutions" width="300"/></a>
</div>

---

<p align="center">
  Made with ❤️ by the BoxLang Team<br/>
  Copyright Since 2023 by <a href="https://www.ortussolutions.com">Ortus Solutions, Corp</a>
</p>

Visit us at [BoxLang.io Plans](https://boxlang.io/plans) for more information.

## Usage

To use it, you need to create a Lambda function and specify `Java 21` as the runtime. The class that executes your BoxLang code is `ortus.boxlang.runtime.aws.LambdaRunner`. By convention it will execute a `Lambda.bx` file in the root (`/var/task/Lambda.bx`) of the Lambda function, via the `run()` method. The method signature can look like this:

```java
// Lambda.bx
class{

	function run( event, context, response ){
		// Your code here
	}

}
```

- The `event` parameter is the event data that is passed to the Lambda function as a `Struct`.
- The `context` parameter is the context object that is passed to the Lambda function. This matches the AWS Lambda context object: `com.amazonaws.services.lambda.runtime.Context`.
- The `response` parameter is the response object that is passed to the Lambda function.

### Response Struct

The `response` object is a `Struct` that you can use to set the response data. The `response` object has the following keys:

- `statusCode` : The HTTP status code for the response.
- `headers` : A `Struct` of headers to send in the response.
- `body` : The body of the response, which can be anything.

The BoxLang lambda runner will return the `response` object as the response to the Lambda function as a JSON object.

### Testing the Lambda Locally

#### Build

```
gradle shadowJar
gradle buildMainZip
```

#### AWS Sam CLI

##### Installing AWS Sam CLI

https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html

#### Invoking your Lambda Locally with Sample Event Data

```
sam local invoke bxFunction --event=workbench/sampleEvents/api.json
sam local invoke bxFunction --event=workbench/sampleEvents/event.json
```

#### Invoking your Lambda Locally with Debug Mode

```
sam local invoke bxFunction --event=workbench/sampleEvents/api.json --debug
sam local invoke bxFunction --event=workbench/sampleEvents/event.json --debug
```

### Custom Lambda Function

If you don't want to use the convention of `Lambda.bx` then you can setup an environment variable called `BOXLANG_LAMBDA_CLASS` with the full path to the BoxLang class that will execute your code. The class must have a `run()` method that matches the signature above.

### Debug Mode

You can enable debug mode by setting the environment variable `BOXLANG_LAMBDA_DEBUG` to `true`. This will output debug information to the Lambda logs.

### Example

Here is an example of a simple Lambda function that returns a `Hello World` response:

```java
// Lambda.bx
class{

	function run( event, context, response ){
		// response.statusCode = 200; set by default
		response.headers = {
			"Content-Type" : "text/plain"
		};
		response.body = "Hello World";
	}

}
```

However, if you don't even want to deal with the `response` struct, you can just use a return and whatever you return will be placed for you in the `response.body`.

```java
// Lambda.bx
class{

	function run( event, context ){
		return "Hello World";
	}

}
```

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
