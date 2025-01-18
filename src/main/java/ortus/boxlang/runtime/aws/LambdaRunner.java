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

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.ModuleService;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
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
 * <p>
 * The runtime is started up and shutdown for each request.
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
	protected static final String		DEFAULT_LAMBDA_CLASS	= "/var/task/Lambda.bx";

	/**
	 * Default lambda folders as per AWS
	 */
	protected static final String		DEFAULT_LAMBDA_ROOT		= "/var/task";

	/**
	 * The header we will use to see if we can execute that method in your lambda
	 */
	protected static final String		BOXLANG_LAMBDA_HEADER	= "x-bx-function";

	/**
	 * Default lambda method
	 */
	protected static final Key			DEFAULT_LAMBDA_METHOD	= Key.run;

	/**
	 * -----------------------------------------------------------------------------
	 * Properties
	 * -----------------------------------------------------------------------------
	 */

	/**
	 * The absolute path to the Lambda.bx file to execute
	 */
	protected Path						lambdaPath;

	/**
	 * Are we in debug mode or not
	 */
	protected Boolean					debugMode				= false;

	/**
	 * The BoxLang config path (if any)
	 */
	protected Path						configPath;

	/**
	 * Lambda Root where it is deployed: /var/task by convention
	 */
	protected String					lambdaRoot				= "";

	/**
	 * The BoxLang runtime
	 */
	protected static final BoxRuntime	runtime;

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

		// Startup the runtime
		// Important: We are using the system temp directory for the runtime since we are stateless
		runtime = BoxRuntime.getInstance( debugMode, configPath, System.getProperty( "java.io.tmpdir" ) );

		// Load Modules by Convention: /var/task/modules if found.
		Path modulesByConventionPath = Path.of( lambdaRoot, "modules" );
		if ( modulesByConventionPath.toFile().exists() ) {
			ModuleService moduleService = runtime.getModuleService();
			runtime.getConfiguration().modulesDirectory.add( modulesByConventionPath.toString() );
			moduleService.registerAll();
			moduleService.activateAll();
		}

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
		this.lambdaRoot	= env.getOrDefault( "LAMBDA_TASK_ROOT", DEFAULT_LAMBDA_ROOT );

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
	 *
	 */
	public Map<?, ?> handleRequest( Map<String, Object> event, Context context ) {
		LambdaLogger logger = context.getLogger();

		// Log the incoming event
		if ( this.debugMode ) {
			logger.log( "Lambda firing with incoming event: " + event );
		}

		// Prep the response
		IStruct		response	= Struct.of(
		    "statusCode", 200,
		    "headers", Struct.of(
		        "Content-Type", "application/json",
		        "Access-Control-Allow-Origin", "*" ),
		    "body", "" );

		// Prepare an execution context
		IBoxContext	boxContext	= new ScriptingRequestBoxContext( runtime.getRuntimeContext() );

		// Prep the incoming event as a struct
		IStruct		eventStruct	= Struct.fromMap( event );

		// Verify the Lambda.bx file
		if ( !lambdaPath.toFile().exists() ) {
			throw new BoxRuntimeException( "Lambda.bx file not found in [" + lambdaPath + "]. So I don't know what to execute for you." );
		}

		// Compile + Get the Lambda Class
		IClassRunnable	lambda			= ( IClassRunnable ) DynamicObject.of(
		    RunnableLoader.getInstance().loadClass( ResolvedFilePath.of( lambdaPath ), boxContext ) )
		    .invokeConstructor( boxContext )
		    .getTargetInstance();

		// Discover the intended lambda method to execute
		Key				lambdaMethod	= getLambdaMethod( event, context );

		// Verify the run method
		if ( !lambda.getThisScope().containsKey( lambdaMethod ) ) {
			throw new BoxRuntimeException( "Lambda.bx file does not contain a [" + lambdaMethod + "] method. So I don't know what to execute for you." );
		}

		// Invoke the run method
		var results = lambda.dereferenceAndInvoke(
		    boxContext,
		    lambdaMethod,
		    new Object[] { eventStruct, context, response },
		    false
		);

		// If results is not null use it as the response
		if ( results != null ) {
			response.put( "body", results );
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
	public Key getLambdaMethod( Map<String, Object> event, Context context ) {
		Key					lambdaMethod	= DEFAULT_LAMBDA_METHOD;

		// Get headers from the event
		@SuppressWarnings( "unchecked" )
		Map<String, String>	headers			= ( Map<String, String> ) event.getOrDefault( "headers", null );

		// Check for the "bx-function" header, else use the default lambda method
		if ( headers != null && headers.containsKey( BOXLANG_LAMBDA_HEADER ) ) {
			String bxFunctionHeader = headers.get( BOXLANG_LAMBDA_HEADER );
			if ( !bxFunctionHeader.isEmpty() ) {
				return Key.of( bxFunctionHeader );
			}
		}

		return lambdaMethod;
	}
}
