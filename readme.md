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

## Welcome to the BoxLang AWS Lambda Runtime

This repository contains the AWS Lambda Runtime for the BoxLang language. This runtime allows you to run BoxLang code in AWS Lambda functions. The runtime is built using the AWS Lambda Custom Runtime API and the BoxLang interpreter.  You can find the full docs here: https://boxlang.ortusbooks.com/getting-started/running-boxlang/aws-lambda

## What is BoxLang?

**BoxLang** is a modern dynamic JVM language that can be deployed on multiple runtimes: operating system (Windows/Mac/*nix/Embedded), web server, lambda, iOS, android, web assembly, and more. **BoxLang** combines many features from different programming languages, including Java, ColdFusion, Python, Ruby, Go, and PHP, to provide developers with a modern and expressive syntax.

**BoxLang** has been designed to be a highly adaptable and dynamic language to take advantage of all the modern features of the JVM and was designed with several goals in mind:

* Be a rapid application development (RAD) scripting language and middleware.
* Unstagnate the dynamic language ecosystem in Java.
* Be dynamic, modular, lightweight, and fast.
* Be 100% interoperable with Java.
* Be modern, functional, and fluent (Think mixing CFML, Node, Kotlin, Java, and Clojure)
* Be able to support multiple runtimes and deployment targets:
  * Native OS Binaries (CLI Tooling, compilers, etc.)
  * MiniServer
  * Servlet Containers - CommandBox/Tomcat/Jetty/JBoss
  * JSR223 Scripting Engines
  * AWS Lambda
  * Microsoft Azure Functions (Coming Soon)
  * Android/iOS Devices (Coming Soon)
  * Web assembly (Coming Soon)
* Compile down to Java ByteCode
* Allow backward compatibility with the existing ColdFusion/CFML language.
* Great IDE, Debugger and Tooling: https://boxlang.ortusbooks.com/getting-started/ide-tooling
* Scripting (Any OS and Shebang) and REPL capabilities

You can find our docs here: https://boxlang.ortusbooks.com/

## License

Apache License, Version 2.0.

## Open-Source & Professional Support

This project is a professional open source project and is available as FREE and open source to use.  Ortus Solutions, Corp provides commercial support, training and commercial subscriptions which include the following:

* Professional Support and Priority Queuing
* Remote Assistance and Troubleshooting
* New Feature Requests and Custom Development
* Custom SLAs
* Application Modernization and Migration Services
* Performance Audits
* Enterprise Modules and Integrations
* Much More

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

## Packaging

In order to deploy your function to AWS Lambda, you need to package the runtime and your BoxLang code into a zip file. The zip file should contain the following structure:

```
+ Lambda.bx
/lib
  + boxlang-aws-lambda-1.0.0-all.jar
```

You can use our source template here: https://github.com/ortus-boxlang/boxlang-aws-lambda-template
to give you a head start in building your serverless applications.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
