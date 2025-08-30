/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.runtime.aws;

import java.nio.file.Path;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.BaseApplicationListener;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.AbortException;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ExceptionUtil;
import ortus.boxlang.runtime.types.util.StringUtil;
import ortus.boxlang.runtime.util.FileSystemUtil;
import ortus.boxlang.runtime.util.ResolvedFilePath;

/**
 * The BoxLang AWS Lambda Runner
 * <p>
 * This class is the entry point for the AWS Lambda runtime. It is responsible
 * for
 * handling the incoming request, invoking the Lambda.bx file, and returning the
 * response.
 * <p>
 * The Lambda.bx file is expected to contain a `run` method that accepts the
 * incoming event,
 * the AWS Lambda context, and the response. The response is expected to be a
 * struct with
 * the following
 * <ul>
 * <li>statusCode: The HTTP status code</li>
 * <li>headers: A struct of headers</li>
 * <li>body: The response body</li>
 * </ul>
 * <p>
 * The Lambda.bx file is expected to be in the current directory and named
 * `Lambda.bx`.
 * <p>
 * The incoming event is expected to be a map of strings.
 * <p>
 * The response is expected to be a JSON string.
 * <p>
 * The Lambda.bx file is compiled and executed using the BoxLang runtime.
 */
public class LambdaRunner implements RequestHandler<Map<String, Object>, Map<?, ?>> {

	/**
	 * -----------------------------------------------------------------------------
	 * Constants
	 * -----------------------------------------------------------------------------
	 */

	/**
	 * The Lambda.bx file name by convention, which is where it's expanded by AWS
	 * Lambda
	 */
	protected static final String						DEFAULT_LAMBDA_CLASS	= "/var/task/Lambda.bx";

	/**
	 * Default lambda folders as per AWS
	 */
	protected static final String						DEFAULT_LAMBDA_ROOT		= "/var/task";

	/**
	 * The header we will use to see if we can execute that method in your lambda
	 */
	protected static final Key							BOXLANG_LAMBDA_HEADER	= Key.of( "x-bx-function" );

	/**
	 * Default lambda method
	 */
	protected static final Key							DEFAULT_LAMBDA_METHOD	= Key.run;

	/**
	 * -----------------------------------------------------------------------------
	 * Properties
	 * -----------------------------------------------------------------------------
	 */

	/**
	 * The absolute path to the Lambda.bx file to execute
	 */
	protected Path										lambdaPath;

	/**
	 * Are we in debug mode or not
	 */
	protected Boolean									debugMode				= false;

	/**
	 * The BoxLang config path (if any)
	 */
	protected Path										configPath;

	/**
	 * Lambda Root where it is deployed: /var/task by convention
	 */
	protected String									lambdaRoot				= "";

	/**
	 * The BoxLang runtime
	 */
	protected static final BoxRuntime					runtime;

	/**
	 * Cache for compiled Lambda classes to avoid recompilation on every invocation
	 */
	protected static final Map<String, IClassRunnable>	compiledLambdaCache		= new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Initialize the BoxLang runtime as fast as possible here.
	 */
	static {
		Map<String, String>	env			= System.getenv();
		Boolean				debugMode	= false;
		String				configPath	= null;
		String				lambdaRoot	= env.getOrDefault( "LAMBDA_TASK_ROOT", DEFAULT_LAMBDA_ROOT );

		// Do we have a BOXLANG_LAMBDA_DEBUGMODE environment variable
		if ( env.get( "BOXLANG_LAMBDA_DEBUGMODE" ) != null ) {
			debugMode = Boolean.parseBoolean( env.get( "BOXLANG_LAMBDA_DEBUGMODE" ) );
		}

		// Do we have a BOXLANG_LAMBDA_CONFIG environment variable
		if ( env.get( "BOXLANG_LAMBDA_CONFIG" ) != null ) {
			configPath = env.get( "BOXLANG_LAMBDA_CONFIG" );
		}
		// Look in the lambda root + boxlang.json
		else if ( Path.of( lambdaRoot, "boxlang.json" ).toFile().exists() ) {
			configPath = Path.of( lambdaRoot, "boxlang.json" ).toString();
		}

		// Performance optimization: Set JVM properties for Lambda environment
		System.setProperty( "aws.lambda.runtime.pool", env.getOrDefault( "BOXLANG_LAMBDA_CONNECTION_POOL_SIZE", "2" ) );

		// Startup the runtime
		// Important: We are using the system temp directory for the runtime since we are stateless
		runtime = BoxRuntime.getInstance( debugMode, configPath, System.getProperty( "java.io.tmpdir" ) );

		// Add a shutdown hook to cleanup the runtime
		Runtime.getRuntime().addShutdownHook( new Thread() {

			@Override
			public void run() {
				System.out.println( "[BoxLang AWS] ShutdownHook triggered" );
				System.out.println( "[BoxLang AWS] Shutting down runtime..." );
				runtime.shutdown();
				System.out.println( "[BoxLang AWS] Runtime gracefully shutdown" );
			}
		} );
	}

	/**
	 * No-arg Constructor required by AWS Lambda
	 */
	public LambdaRunner() {
		// Get a Path to the Lambda.bx file from the class loader
		this( Path.of( DEFAULT_LAMBDA_CLASS ).toAbsolutePath(), false );
	}

	/**
	 * Constructor: Useful for tests and mocking
	 *
	 * @param lambdaPath The absolute path to the Lambda.bx file
	 * @param debugMode  Are we in debug mode or not
	 */
	public LambdaRunner( Path lambdaPath, Boolean debugMode ) {
		Map<String, String> env = System.getenv();
		this.lambdaPath	= lambdaPath;
		this.debugMode	= debugMode;

		// For tests, derive lambdaRoot from the provided lambdaPath parent directory
		// For production, use environment variable or default
		if ( lambdaPath.toString().contains( "test/resources" ) ) {
			this.lambdaRoot = lambdaPath.getParent().toString();
		} else {
			this.lambdaRoot = env.getOrDefault( "LAMBDA_TASK_ROOT", DEFAULT_LAMBDA_ROOT );
		}

		// Check if there is a BOXLANG_LAMBDA_CLASS environment variable and use that
		// instead
		if ( env.get( "BOXLANG_LAMBDA_CLASS" ) != null ) {
			this.lambdaPath = Path.of( env.get( "BOXLANG_LAMBDA_CLASS" ) ).toAbsolutePath();
		}

		// Do we have a BOXLANG_LAMBDA_DEBUGMODE environment variable
		if ( env.get( "BOXLANG_LAMBDA_DEBUGMODE" ) != null ) {
			this.debugMode = Boolean.parseBoolean( env.get( "BOXLANG_LAMBDA_DEBUGMODE" ) );
		}

		if ( this.debugMode ) {
			System.out.println( "Lambda configured with the following path: " + this.lambdaPath );
			System.out.println( "Lambda root directory: " + this.lambdaRoot );
		}
	}

	/**
	 * Get the lambda path
	 *
	 * @return The absolute path to the Lambda.bx file
	 */
	public Path getLambdaPath() {
		return this.lambdaPath;
	}

	/**
	 * Are we in debug mode
	 *
	 * @return True if we are in debug mode
	 */
	public Boolean inDebugMode() {
		return this.debugMode;
	}

	/**
	 * Set the lambda path
	 *
	 * @param lambdaPath The absolute path to the Lambda.bx file
	 *
	 * @return The LambdaRunner instance
	 */
	public LambdaRunner setLambdaPath( Path lambdaPath ) {
		this.lambdaPath = lambdaPath;
		return this;
	}

	/**
	 * Get the BoxLang runtime
	 */
	public BoxRuntime getRuntime() {
		return runtime;
	}

	/**
	 * Handle the incoming request from the AWS Lambda
	 *
	 * @param event   The incoming event as a Struct
	 * @param context The AWS Lambda context
	 *
	 * @throws BoxRuntimeException If the Lambda.bx file is not found or does not contain a `run` method
	 *
	 * @return The response as a JSON string
	 */
	public Map<?, ?> handleRequest( Map<String, Object> event, Context context ) {
		LambdaLogger	logger		= context.getLogger();
		long			startTime	= System.currentTimeMillis();

		// Log the incoming event
		if ( this.debugMode ) {
			logger.log( "Lambda firing with incoming event: " + event );
			logger.log( "Lambda memory limit: " + context.getMemoryLimitInMB() + "MB" );
			logger.log( "Lambda remaining time: " + context.getRemainingTimeInMillis() + "ms" );
		}

		// Prep a response struct
		IStruct					response					= Struct.of(
		    "statusCode", 200,
		    "headers", Struct.of(
		        "Content-Type", "application/json",
		        "Access-Control-Allow-Origin", "*" ),
		    "body", "",
		    "cookies", new Array()
		);

		// Convert the incoming event as a BoxLang struct first
		IStruct					eventStruct					= Struct.fromMap( event );

		// Prepare an execution context and do full Application.bx life-cycle checks
		// First, try to resolve class from URI path if URI routing is enabled
		String					uriPath						= extractUriPath( eventStruct );
		Path					resolvedClassPath			= resolveClassFromUri( uriPath, this.lambdaRoot );
		Path					finalLambdaPath				= resolvedClassPath != null ? resolvedClassPath : lambdaPath;

		ResolvedFilePath		resolvedLambdaPath			= ResolvedFilePath.of( finalLambdaPath );
		String					resolvedLambdaPathString	= resolvedLambdaPath.absolutePath().toString();
		IBoxContext				boxContext					= new ScriptingRequestBoxContext(
		    runtime.getRuntimeContext(),
		    FileSystemUtil.createFileUri( resolvedLambdaPath.absolutePath().toString() )
		);

		// Set threading Context and prep for request
		BaseApplicationListener	listener					= boxContext.getParentOfType( RequestBoxContext.class ).getApplicationListener();
		RequestBoxContext.setCurrent( boxContext.getParentOfType( RequestBoxContext.class ) );
		Throwable	errorToHandle	= null;
		Object		lambdaResult	= null;

		try {
			// Compile + Get the Lambda Class with caching
			IClassRunnable lambda = getOrCompileLambda( resolvedLambdaPath, boxContext );

			// Discover the intended lambda method to execute
			Key lambdaMethod = getLambdaMethod( eventStruct, context );

			// Invoke the onRequestStart method
			listener.onRequestStart( boxContext, new Object[] { resolvedLambdaPathString, eventStruct, context } );
			// Invoke the Lambda method
			lambdaResult = lambda.dereferenceAndInvoke(
			    boxContext,
			    lambdaMethod,
			    new Object[] { eventStruct, context, response },
			    false
			);
		} catch ( AbortException e ) {

			if ( this.debugMode ) {
				logger.log( "AbortException", LogLevel.DEBUG );
			}

			try {
				listener.onAbort( boxContext, new Object[] { resolvedLambdaPathString, eventStruct, context } );
			} catch ( Throwable ae ) {
				// Opps, an error while handling onAbort
				errorToHandle = ae;
			}
			boxContext.flushBuffer( true );
			if ( e.getCause() != null ) {
				// This will always be an instance of CustomException
				throw ( RuntimeException ) e.getCause();
			}
		} catch ( Exception e ) {
			errorToHandle = e;
		} finally {

			try {
				listener.onRequestEnd( boxContext, new Object[] { resolvedLambdaPathString, eventStruct, context } );
			} catch ( Throwable e ) {
				// Opps, an error while handling onRequestEnd
				errorToHandle = e;
			}

			// Make sure the buffer is flushed
			boxContext.flushBuffer( false );

			// If we have an error to handle, do so
			if ( errorToHandle != null ) {

				// Log it
				logger.log( errorToHandle.getMessage(), LogLevel.ERROR );

				try {
					// A return of true means the error has been "handled". False means the default
					// error handling should be used
					if ( !listener.onError( boxContext, new Object[] { errorToHandle, "", eventStruct, context } ) ) {
						throw errorToHandle;
					}
					// This is a failsafe in case the onError blows up.
				} catch ( Throwable t ) {
					errorToHandle.printStackTrace();
					ExceptionUtil.throwException( t );
				}
			}

			boxContext.flushBuffer( false );
		}

		// If results is not null use it as the response
		if ( lambdaResult != null ) {
			response.put( "body", lambdaResult );
		}

		// Log performance metrics if in debug mode
		if ( this.debugMode ) {
			long executionTime = System.currentTimeMillis() - startTime;
			logger.log( "Lambda execution time: " + executionTime + "ms" );
			logger.log( "Lambda remaining time after execution: " + context.getRemainingTimeInMillis() + "ms" );
		}

		// Lambdas marshall the Map to a JSON string
		return response;
	}

	/**
	 * Discover the intended lambda method to execute
	 *
	 * @param event   The incoming event
	 * @param context The AWS Lambda context
	 *
	 * @return The lambda method to execute
	 */
	public Key getLambdaMethod( IStruct event, Context context ) {
		Key		lambdaMethod	= DEFAULT_LAMBDA_METHOD;
		IStruct	headers			= StructCaster.cast( event.getOrDefault( "headers", new Struct() ) );

		// Check for the "bx-function" header, else use the default lambda method
		if ( headers.containsKey( BOXLANG_LAMBDA_HEADER ) ) {
			String bxFunctionHeader = StringCaster.cast( headers.get( BOXLANG_LAMBDA_HEADER ) );
			if ( !bxFunctionHeader.isEmpty() ) {
				return Key.of( bxFunctionHeader );
			}
		}

		return lambdaMethod;
	}

	/**
	 * Extract the URI path from various AWS event types
	 * Supports API Gateway, Lambda Function URLs, ALB, and direct invocations
	 *
	 * @param event The incoming event
	 *
	 * @return The URI path or null if not found/applicable
	 */
	public String extractUriPath( IStruct event ) {
		// Check for API Gateway v2.0 format (HTTP API)
		if ( event.containsKey( "requestContext" ) ) {
			IStruct requestContext = StructCaster.cast( event.get( "requestContext" ) );

			// API Gateway v2.0 (HTTP API)
			if ( requestContext.containsKey( "http" ) ) {
				IStruct httpContext = StructCaster.cast( requestContext.get( "http" ) );
				if ( httpContext.containsKey( "path" ) ) {
					return StringCaster.cast( httpContext.get( "path" ) );
				}
			}

			// API Gateway v1.0 (REST API) - check for resourcePath
			if ( requestContext.containsKey( "resourcePath" ) ) {
				return StringCaster.cast( requestContext.get( "resourcePath" ) );
			}
		}

		// Check for Lambda Function URL format
		if ( event.containsKey( "requestContext" ) ) {
			IStruct requestContext = StructCaster.cast( event.get( "requestContext" ) );
			if ( requestContext.containsKey( "domainName" ) && event.containsKey( "rawPath" ) ) {
				return StringCaster.cast( event.get( "rawPath" ) );
			}
		}

		// Check for ALB (Application Load Balancer) format
		if ( event.containsKey( "requestContext" ) ) {
			IStruct requestContext = StructCaster.cast( event.get( "requestContext" ) );
			if ( requestContext.containsKey( "elb" ) && event.containsKey( "path" ) ) {
				return StringCaster.cast( event.get( "path" ) );
			}
		}

		// Direct path check for API Gateway v1.0
		if ( event.containsKey( "path" ) ) {
			return StringCaster.cast( event.get( "path" ) );
		}

		return null;
	}

	/**
	 * Resolve the BoxLang class path based on the URI path
	 * Converts URI paths like "/products", "/customers/123" to class files like "Products.bx", "Customers.bx"
	 *
	 * @param uriPath    The URI path from the request
	 * @param lambdaRoot The root directory where BoxLang classes are located
	 *
	 * @return The resolved Path to the BoxLang class file, or null if path is invalid
	 */
	public Path resolveClassFromUri( String uriPath, String lambdaRoot ) {
		if ( uriPath == null || uriPath.isEmpty() || uriPath.equals( "/" ) ) {
			return null;
		}

		// Remove leading slash and extract the first segment
		String		cleanPath		= uriPath.startsWith( "/" ) ? uriPath.substring( 1 ) : uriPath;

		// Split by "/" and take the first segment (resource name)
		String[]	pathSegments	= cleanPath.split( "/" );
		if ( pathSegments.length == 0 || pathSegments[ 0 ].isEmpty() ) {
			return null;
		}

		// Convert first segment to PascalCase for class name
		String	resourceName	= pathSegments[ 0 ];
		String	className		= StringUtil.pascalCase( resourceName ) + ".bx";

		// Construct the full path
		Path	classPath		= Path.of( lambdaRoot, className );

		// Check if the file exists
		if ( classPath.toFile().exists() ) {
			if ( this.debugMode ) {
				System.out.println( "URI routing: " + uriPath + " -> " + classPath );
			}
			return classPath.toAbsolutePath();
		}

		if ( this.debugMode ) {
			System.out.println( "URI routing: Class not found for " + uriPath + " (looked for " + classPath + ")" );
		}

		return null;
	}

	/**
	 * Get or compile the Lambda class with caching to improve performance
	 *
	 * @param resolvedLambdaPath The resolved path to the Lambda file
	 * @param boxContext         The BoxLang context
	 *
	 * @return The compiled Lambda class instance
	 */
	private IClassRunnable getOrCompileLambda( ResolvedFilePath resolvedLambdaPath, IBoxContext boxContext ) {
		String			lambdaKey		= resolvedLambdaPath.absolutePath().toString();

		// Check if we already have this Lambda compiled and cached
		IClassRunnable	cachedLambda	= compiledLambdaCache.get( lambdaKey );
		if ( cachedLambda != null ) {
			if ( this.debugMode ) {
				System.out.println( "Using cached Lambda class for: " + lambdaKey );
			}
			return cachedLambda;
		}

		// Compile the Lambda class
		if ( this.debugMode ) {
			System.out.println( "Compiling Lambda class for: " + lambdaKey );
		}

		IClassRunnable lambda = ( IClassRunnable ) DynamicObject.of(
		    RunnableLoader.getInstance().loadClass( resolvedLambdaPath, boxContext )
		).invokeConstructor( boxContext ).getTargetInstance();

		// Cache it for future use
		compiledLambdaCache.put( lambdaKey, lambda );

		return lambda;
	}
}
