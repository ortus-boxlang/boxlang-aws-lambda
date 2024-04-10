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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.jr.ob.JSON.Feature;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.util.JSONUtil;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;

/**
 * The BoxLang AWS Lambda Runner
 * <p>
 * This class is the entry point for the AWS Lambda runtime. It is responsible for
 * handling the incoming request, invoking the Lambda.bx file, and returning the response.
 * <p>
 * The Lambda.bx file is expected to contain a `run` method that accepts the incoming event,
 * the AWS Lambda context, and the response. The response is expected to be a struct with
 * the following
 * <ul>
 * <li>statusCode: The HTTP status code</li>
 * <li>headers: A struct of headers</li>
 * <li>body: The response body</li>
 * </ul>
 * <p>
 * The Lambda.bx file is expected to be in the current directory and named `Lambda.bx`.
 * <p>
 * The incoming event is expected to be a map of strings.
 * <p>
 * The response is expected to be a JSON string.
 * <p>
 * The Lambda.bx file is compiled and executed using the BoxLang runtime.
 * <p>
 * The runtime is started up and shutdown for each request.
 */
public class LambdaRunner implements RequestHandler<Map<String, String>, String> {

	/**
	 * The Lambda.bx file name by convention, which is where it's expanded by AWS Lambda
	 */
	public static final String	LAMBDA_CLASS	= "/var/task/Lambda.bx";

	/**
	 * The absolute path to the Lambda.bx file to execute
	 */
	private Path				lambdaPath;

	/**
	 * Are we in debug mode or not
	 */
	private Boolean				debugMode		= false;

	/**
	 * Constructor
	 */
	public LambdaRunner() {
		// Get a Path to the Lambda.bx file from the class loader
		this( Path.of( LAMBDA_CLASS ).toAbsolutePath(), false );
	}

	/**
	 * Constructor: Useful for tests
	 *
	 * @param lambdaPath The absolute path to the Lambda.bx file
	 * @param debugMode  Are we in debug mode or not
	 */
	public LambdaRunner( Path lambdaPath, Boolean debugMode ) {
		this.lambdaPath	= lambdaPath;
		this.debugMode	= debugMode;

		// Check if there is a BOXLANG_LAMBDA_CLASS environment variable and use that instead
		if ( System.getenv( "BOXLANG_LAMBDA_CLASS" ) != null ) {
			this.lambdaPath = Path.of( System.getenv( "BOXLANG_LAMBDA_CLASS" ) ).toAbsolutePath();
		}

		// Do we have a BOXLANG_LAMBDA_DEBUGMODE environment variable
		if ( System.getenv( "BOXLANG_LAMBDA_DEBUGMODE" ) != null ) {
			this.debugMode = Boolean.parseBoolean( System.getenv( "BOXLANG_LAMBDA_DEBUGMODE" ) );
		}

		// Log the lambda path if in debug mode
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
	 * Handle the incoming request from the AWS Lambda
	 *
	 * @param event   The incoming event as a Struct
	 * @param context The AWS Lambda context
	 *
	 * @return The response as a JSON string
	 *
	 */
	@Override
	public String handleRequest( Map<String, String> event, Context context ) {
		LambdaLogger logger = context.getLogger();

		// Log the incoming event
		if ( this.debugMode ) {
			logger.log( "Lambda firing with incoming event: " + event );
		}

		// Startup the runtime
		BoxRuntime	runtime		= BoxRuntime.getInstance( this.debugMode );
		IBoxContext	boxContext	= new ScriptingRequestBoxContext( runtime.getRuntimeContext() );

		// Prep the response
		IStruct		response	= Struct.of(
		    "statusCode", 200,
		    "headers", new Struct(),
		    "body", "" );

		// Prep the incoming event as a struct
		IStruct		eventStruct	= Struct.fromMap( event );

		try {

			// Verify the Lambda.bx file
			if ( !lambdaPath.toFile().exists() ) {
				throw new BoxRuntimeException( "Lambda.bx file not found in [" + lambdaPath + "]" );
			}

			// Compile + Get the Lambda Class
			IClassRunnable lambda = ( IClassRunnable ) DynamicObject.of(
			    RunnableLoader.getInstance().loadClass( lambdaPath, this.getClass().getPackageName(), boxContext )
			)
			    .invokeConstructor( boxContext )
			    .getTargetInstance();

			// Verify the run method
			if ( !lambda.getThisScope().containsKey( Key.run ) ) {
				throw new BoxRuntimeException( "Lambda.bx file does not contain a `run` method" );
			}

			// Invoke the run method
			var results = lambda.dereferenceAndInvoke(
			    boxContext,
			    Key.run,
			    new Object[] { eventStruct, context, response },
			    false
			);

			// If results is not null use it as the response
			if ( results != null ) {
				response.put( "body", results );
			}

			// Response back to JSON
			return JSONUtil.getJSONBuilder()
			    .with( Feature.PRETTY_PRINT_OUTPUT, Feature.WRITE_NULL_PROPERTIES )
			    .asString( response );
		} catch (

		IOException e ) {
			throw new BoxRuntimeException( "Error converting response to JSON", e );
		} finally {
			runtime.shutdown();
		}
	}
}
