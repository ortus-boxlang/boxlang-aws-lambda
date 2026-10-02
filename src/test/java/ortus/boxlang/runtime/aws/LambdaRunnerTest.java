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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;

import ortus.boxlang.runtime.aws.mocks.TestContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class LambdaRunnerTest {

	private static final Logger logger = LoggerFactory.getLogger( LambdaRunnerTest.class );

	@Test
	@DisplayName( "LambdaRunner throws BoxRuntimeException when Lambda.bx not found" )
	public void testLambdaNotFound() throws IOException {
		// Set a non-existent path
		Path			invalidPath	= Path.of( "invalid_path", "Lambda.bx" );
		LambdaRunner	runner		= new LambdaRunner( invalidPath, true );
		// Create a AWS Lambda Context
		Context			context		= new TestContext();
		var				event		= new HashMap<String, Object>();

		// Expect BoxRuntimeException
		assertThrows( RuntimeException.class, () -> runner.handleRequest( event, context ) );
	}

	@DisplayName( "Test a valid Lambda.bx" )
	@Test
	public void testValidLambda() throws IOException {
		// Set a valid path
		Path			validPath	= Path.of( "src", "test", "resources", "Lambda.bx" );
		LambdaRunner	runner		= new LambdaRunner( validPath, true );
		// Create a AWS Lambda Context
		Context			context		= new TestContext();
		var				event		= new HashMap<String, Object>();
		// Add some mock data to the event
		event.put( "name", "Ortus Solutions" );
		event.put( "when", Instant.now().toString() );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		System.out.println( "====> RESPONSE " + response );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
	}

	@DisplayName( "Test a valid Lambda.bx with a header bx-function" )
	@Test
	public void testValidLambdaWithHeader() throws IOException {
		// Set a valid path
		Path			validPath	= Path.of( "src", "test", "resources", "Lambda.bx" );
		LambdaRunner	runner		= new LambdaRunner( validPath, true );
		// Create a AWS Lambda Context
		Context			context		= new TestContext();
		var				event		= new HashMap<String, Object>();
		// Add some mock data to the event
		event.put( "name", "Ortus Solutions" );
		event.put( "when", Instant.now().toString() );
		// Add a header to the event
		event.put( "headers", new HashMap<String, Object>() {

			{
				put( "x-bx-function", "hello" );
			}
		} );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		System.out.println( "====> RESPONSE " + response );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		assertThat( response.getAsString( Key.of( "body" ) ) ).isEqualTo( "Hello Baby" );
	}

	// ===================================
	// URI ROUTING TESTS
	// ===================================

	@DisplayName( "Test URI routing to Products.bx with API Gateway v2.0 event" )
	@Test
	public void testUriRoutingToProducts() throws IOException {
		// Set test resource path as lambda root
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create API Gateway v2.0 event structure for /products
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "routeKey", "GET /products" );
		event.put( "rawPath", "/products" );
		event.put( "headers", new HashMap<String, Object>() {

			{
				put( "accept", "application/json" );
			}
		} );

		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/products" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// Verify it's actually using Products.bx by checking the response content
		// The body could be either an IStruct (complex object) or String (JSON-serialized)
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "Test: Fetching all products" );
			assertThat( body.getAsInteger( Key.of( "total" ) ) ).isEqualTo( 2 );
		} else {
			// Body is a JSON string, parse it or check for expected content
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "Test: Fetching all products" );
			assertThat( bodyStr ).contains( "\"total\":2" );
		}
	}

	@DisplayName( "Test URI routing to Products.bx with path parameter" )
	@Test
	public void testUriRoutingToProductWithId() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create API Gateway event for /products/123
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "routeKey", "GET /products/{id}" );
		event.put( "rawPath", "/products/123" );
		event.put( "pathParameters", new HashMap<String, Object>() {

			{
				put( "id", "123" );
			}
		} );

		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/products/123" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// The body could be either an IStruct or String (JSON-serialized)
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "Test: Fetching product #123" );
			IStruct data = ( IStruct ) body.get( Key.of( "data" ) );
			assertThat( data.getAsString( Key.of( "id" ) ) ).isEqualTo( "123" );
		} else {
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "Test: Fetching product #123" );
			assertThat( bodyStr ).contains( "\"id\":\"123\"" );
		}
	}

	@DisplayName( "Test URI routing to Customers.bx" )
	@Test
	public void testUriRoutingToCustomers() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create API Gateway event for /customers
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/customers" );

		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/customers" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// Check response body content
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "Test: Fetching all customers" );
			assertThat( body.getAsInteger( Key.of( "total" ) ) ).isEqualTo( 2 );
		} else {
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "Test: Fetching all customers" );
			assertThat( bodyStr ).contains( "\"total\":2" );
		}
	}

	@DisplayName( "Test URI routing with hyphenated path to UserProfiles.bx" )
	@Test
	public void testUriRoutingWithHyphenatedPath() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create API Gateway event for /user-profiles (should route to UserProfiles.bx)
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/user-profiles" );

		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/user-profiles" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// Check response body content
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "UserProfiles handling hyphenated URI" );
			assertThat( body.getAsString( Key.of( "route" ) ) ).isEqualTo( "user-profiles -> UserProfiles.bx" );
		} else {
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "UserProfiles handling hyphenated URI" );
			assertThat( bodyStr ).contains( "user-profiles -> UserProfiles.bx" );
		}
	}

	@DisplayName( "Test URI routing fallback to Lambda.bx when class not found" )
	@Test
	public void testUriRoutingFallbackToLambda() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create API Gateway event for /nonexistent (should fallback to Lambda.bx)
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/nonexistent" );
		event.put( "name", "Fallback Test" );

		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/nonexistent" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		// Should fallback to Lambda.bx which returns void from run method
		// so body should be empty string or contain the event data
	}

	@DisplayName( "Test URI routing with Lambda Function URL event format" )
	@Test
	public void testUriRoutingWithFunctionUrl() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create Lambda Function URL event structure for /products
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/products" );
		event.put( "headers", new HashMap<String, Object>() {

			{
				put( "accept", "application/json" );
			}
		} );

		var requestContext = new HashMap<String, Object>();
		requestContext.put( "domainName", "abcd1234.lambda-url.us-east-1.on.aws" );
		requestContext.put( "http", new HashMap<String, Object>() {

			{
				put( "method", "GET" );
			}
		} );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// Check response body content
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "Test: Fetching all products" );
		} else {
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "Test: Fetching all products" );
		}
	}

	@DisplayName( "Test URI routing with API Gateway v1.0 event format" )
	@Test
	public void testUriRoutingWithApiGatewayV1() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create API Gateway v1.0 event structure for /customers
		var				event		= new HashMap<String, Object>();
		event.put( "path", "/customers" );
		event.put( "httpMethod", "GET" );
		event.put( "headers", new HashMap<String, Object>() {

			{
				put( "accept", "application/json" );
			}
		} );

		var requestContext = new HashMap<String, Object>();
		requestContext.put( "resourcePath", "/customers" );
		requestContext.put( "httpMethod", "GET" );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// Check response body content
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "Test: Fetching all customers" );
		} else {
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "Test: Fetching all customers" );
		}
	}

	@DisplayName( "Test URI routing with nested path uses first segment" )
	@Test
	public void testUriRoutingWithNestedPath() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Create event for /products/categories/electronics (should still route to Products.bx)
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/products/categories/electronics" );

		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/products/categories/electronics" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );

		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );

		// Should route to Products.bx based on first segment "/products"
		Object bodyObj = response.get( Key.of( "body" ) );
		if ( bodyObj instanceof IStruct ) {
			IStruct body = ( IStruct ) bodyObj;
			assertThat( body.getAsString( Key.of( "message" ) ) ).contains( "Test: Fetching all products" );
		} else {
			String bodyStr = bodyObj.toString();
			assertThat( bodyStr ).contains( "Test: Fetching all products" );
		}
	}

	// ===================================
	// MANIFEST / HANDLERS ROUTING TESTS
	// ===================================

	@DisplayName( "Test manifest.json is authoritative: routes what it lists, ignores what it doesn't" )
	@Test
	public void testManifestRoutingIsAuthoritative() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "manifestRouting" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// A route listed in manifest.json resolves to its handler
		assertThat( runner.getHandlerRoutes() ).containsKey( "products" );

		var event = new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/products" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/products" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		String	bodyStr	= bodyObj instanceof IStruct body ? body.getAsString( Key.of( "message" ) ) : bodyObj.toString();
		assertThat( bodyStr ).contains( "Manifest-routed: Products handler" );

		// Decoy.bx exists on disk (in handlers/) but is NOT listed in manifest.json:
		// it must never be reachable, proving the manifest is the allowlist, not
		// merely a hint that the handlers/ directory happens to exist.
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "decoy" );
		assertThat( runner.resolveClassFromUri( "/decoy" ) ).isNull();
	}

	@DisplayName( "Test handlers/ directory boot-scan supports nested, case-insensitive routes" )
	@Test
	public void testHandlersDirectoryNestedRouting() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "handlersRouting" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// handlers/Api/Test.bx (mixed-case directory) registers as "api/test"
		assertThat( runner.getHandlerRoutes() ).containsKey( "api/test" );

		var event = new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/api/test" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/api/test" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		String	bodyStr	= bodyObj instanceof IStruct body ? body.getAsString( Key.of( "message" ) ) : bodyObj.toString();
		assertThat( bodyStr ).contains( "Nested handler: api/test" );
	}

	@DisplayName( "Test Application.bx and the default Lambda class are never routable targets" )
	@Test
	public void testReservedFilesNeverRouted() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "reservedRouting" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );

		// Neither Application.bx nor Lambda.bx should ever appear in the routing table,
		// even under the legacy root-scan fallback (no handlers/ or manifest.json here) -
		// this is the exact scenario the reported vulnerability exploited:
		// GET /application + x-bx-function: onApplicationStart
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "application" );
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "lambda" );
		assertThat( runner.resolveClassFromUri( "/application" ) ).isNull();
	}

	@DisplayName( "Test a corrupt manifest.json restricts routing to the default handler only, instead of widening to a directory scan" )
	@Test
	public void testCorruptManifestRestrictsToDefaultHandlerOnly() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "corruptManifest" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );

		// manifest.json is invalid JSON, and a handlers/Foo.bx directory also exists - but a
		// present-and-corrupt manifest.json is a build/deploy error, not license to widen
		// routing by falling back to a directory scan. Only the default handler is reachable.
		assertThat( runner.getHandlerRoutes() ).isEmpty();
	}

	// ===================================
	// APPLICATION.BX LIFECYCLE TESTS
	// ===================================

	@DisplayName( "Test Application.bx onRequestStart fires for the default Lambda.bx handler" )
	@Test
	public void testApplicationLifecycleFiresForDefaultHandler() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "applicationLifecycle" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		boolean	fired	= bodyObj instanceof IStruct body ? body.getAsBoolean( Key.of( "applicationBxFired" ) )
		    : bodyObj.toString().contains( "\"applicationBxFired\":true" );
		assertThat( fired ).isTrue();
	}

	@DisplayName( "Test Application.bx onRequestStart also fires when URI routing dispatches to a handlers/ class" )
	@Test
	public void testApplicationLifecycleFiresForRoutedHandler() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "applicationLifecycle" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// Sanity check: the request really is being routed to handlers/Products.bx, not
		// silently falling back to the default Lambda.bx
		assertThat( runner.getHandlerRoutes() ).containsKey( "products" );

		var event = new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/products" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/products" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		// Before the fix, Application.bx was looked up relative to handlers/, where it
		// doesn't exist, so onRequestStart never fired and this would be false.
		boolean	fired	= bodyObj instanceof IStruct body ? body.getAsBoolean( Key.of( "applicationBxFired" ) )
		    : bodyObj.toString().contains( "\"applicationBxFired\":true" );
		assertThat( fired ).isTrue();
	}

	// ===================================
	// OPT-IN LEGACY ROOT SCAN
	// ===================================

	@DisplayName( "Test the legacy root-directory scan is on by default, matching prior releases" )
	@Test
	public void testRootScanEnabledByDefault() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "rootScanDisabled" );
		// null = defer to BOXLANG_ENABLE_ROOT_SCAN, which defaults to true when unset
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true, null );

		assertThat( runner.getHandlerRoutes() ).containsKey( "transport" );
	}

	@DisplayName( "Test BOXLANG_ENABLE_ROOT_SCAN=false restricts the no-manifest/no-handlers fallback to the default handler only" )
	@Test
	public void testRootScanCanBeDisabled() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "rootScanDisabled" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true, false );

		// Transport.bx exists on disk at the root, but with root scanning disabled it must
		// never become a routable target - this is exactly the client-reported scenario:
		// GET /transport + x-bx-function: readRequest reaching an internal class.
		assertThat( runner.getHandlerRoutes() ).isEmpty();
		assertThat( runner.resolveClassFromUri( "/transport" ) ).isNull();
		assertThat( runner.resolveClassFromUri( "/TRANSPORT" ) ).isNull();
		assertThat( runner.resolveClassFromUri( "/trans-port" ) ).isNull();

		Context	context	= new TestContext();
		var		event	= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/transport" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/transport" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		// With no route registered for /transport, this must fall back to the default
		// handler (Lambda.bx), not reach Transport.bx.
		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		String	bodyStr	= bodyObj instanceof IStruct body ? body.getAsString( Key.of( "message" ) ) : bodyObj.toString();
		assertThat( bodyStr ).contains( "default lambda" );
	}

	// ===================================
	// MANIFEST ENFORCEMENT: reserved + defaultHandler
	// ===================================

	@DisplayName( "Test manifest.json cannot route to Application.bx, Lambda.bx, or its own declared reserved files" )
	@Test
	public void testManifestReservedListIsEnforced() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "manifestReservedEnforced" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );

		// Built-in reserved names, even though the manifest explicitly lists routes for them
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "application" );
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "lambda" );
		// The manifest's own "reserved" array names Secret.bx, even though it's neither
		// Application.bx nor the default handler
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "secret" );
		assertThat( runner.resolveClassFromUri( "/application" ) ).isNull();
		assertThat( runner.resolveClassFromUri( "/secret" ) ).isNull();

		// A legitimate, non-reserved route from the same manifest still works
		assertThat( runner.getHandlerRoutes() ).containsKey( "products" );

		Context	context	= new TestContext();
		var		event	= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/products" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/products" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
	}

	@DisplayName( "Test manifest.json defaultHandler.file/method is respected instead of the Lambda.bx/run() convention" )
	@Test
	public void testManifestDefaultHandlerIsRespected() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "manifestDefaultHandler" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// No routes are declared, so every request falls through to the default handler -
		// which the manifest overrides to handlers/Special.bx#handle(), not Lambda.bx#run()
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/anything" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/anything" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		String	bodyStr	= bodyObj instanceof IStruct body ? body.getAsString( Key.of( "message" ) ) : bodyObj.toString();
		assertThat( bodyStr ).contains( "manifest-declared default handler" );
	}

	@DisplayName( "Test manifest.json defaultHandler.file pointing at Application.bx hard-aborts cold start" )
	@Test
	public void testManifestDefaultHandlerReservedHardAborts() {
		Path									testPath	= Path.of( "src", "test", "resources", "manifestDefaultHandlerReserved" );

		LambdaRunner.ReservedHandlerException	thrown		= assertThrows(
		    LambdaRunner.ReservedHandlerException.class,
		    () -> new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true )
		);
		assertThat( thrown.getMessage() ).contains( "reserved" );
		assertThat( thrown.getMessage() ).contains( "Application.bx" );
	}

	@DisplayName( "Test manifest.json handlers[*].file cannot escape the lambda root via ../ path traversal" )
	@Test
	public void testManifestHandlerPathTraversalIsRejected() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "manifestPathTraversal", "app" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );

		// The manifest declares "secret" -> "../outside/Secret.bx"; despite that file
		// genuinely existing, it must never be registered as a route since it resolves
		// outside the lambda root.
		assertThat( runner.getHandlerRoutes() ).doesNotContainKey( "secret" );
	}

	@DisplayName( "Test manifest.json defaultHandler.file cannot escape the lambda root via ../ path traversal" )
	@Test
	public void testManifestDefaultHandlerPathTraversalIsRejected() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "manifestDefaultHandlerTraversal", "app" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );
		Context			context		= new TestContext();

		// defaultHandler.file points outside the lambda root via ../ - the conventional
		// Lambda.bx must remain in effect rather than the out-of-root Secret.bx
		var				event		= new HashMap<String, Object>();
		event.put( "version", "2.0" );
		event.put( "rawPath", "/anything" );
		var	requestContext	= new HashMap<String, Object>();
		var	httpContext		= new HashMap<String, Object>();
		httpContext.put( "method", "GET" );
		httpContext.put( "path", "/anything" );
		requestContext.put( "http", httpContext );
		event.put( "requestContext", requestContext );

		IStruct response = ( IStruct ) runner.handleRequest( event, context );
		assertThat( response.getAsInteger( Key.of( "statusCode" ) ) ).isEqualTo( 200 );
		Object	bodyObj	= response.get( Key.of( "body" ) );
		String	bodyStr	= bodyObj instanceof IStruct body ? body.getAsString( Key.of( "message" ) ) : bodyObj.toString();
		assertThat( bodyStr ).contains( "conventional default lambda" );
	}

	// ===================================
	// RESPONSE MODE + HOOK RESPONSE TESTS
	// ===================================

	private static Path fixture( String name ) {
		return Path.of( "src", "test", "resources", name, "Lambda.bx" );
	}

	private static HashMap<String, Object> eventWith( Object... keyValues ) {
		var event = new HashMap<String, Object>();
		for ( int i = 0; i < keyValues.length; i += 2 ) {
			event.put( ( String ) keyValues[ i ], keyValues[ i + 1 ] );
		}
		return event;
	}

	@DisplayName( "Test http mode (the default) keeps the envelope, and onRequestEnd can wrap the body via the response struct" )
	@Test
	public void testHttpModeEnvelopeAndOnRequestEndWrap() {
		LambdaRunner	runner		= new LambdaRunner( fixture( "responseHooks" ), true );
		IStruct			response	= ( IStruct ) runner.handleRequest( eventWith(), new TestContext() );

		assertThat( response.getAsInteger( Key.statusCode ) ).isEqualTo( 200 );
		assertThat( response.containsKey( Key.headers ) ).isTrue();
		IStruct body = ( IStruct ) response.get( Key.body );
		assertThat( body.get( Key.of( "ok" ) ) ).isEqualTo( true );
		assertThat( ( ( IStruct ) body.get( Key.of( "data" ) ) ).get( Key.of( "name" ) ) ).isEqualTo( "Luis" );
	}

	@DisplayName( "Test raw mode returns only the unwrapped body, with no statusCode/headers/cookies envelope" )
	@Test
	public void testRawModeReturnsOnlyTheBody() {
		LambdaRunner	runner	= new LambdaRunner( fixture( "responseHooks" ), true, null, "raw" );
		IStruct			result	= ( IStruct ) runner.handleRequest( eventWith(), new TestContext() );

		assertThat( result.get( Key.of( "ok" ) ) ).isEqualTo( true );
		assertThat( ( ( IStruct ) result.get( Key.of( "data" ) ) ).get( Key.of( "id" ) ) ).isEqualTo( 1 );
		assertThat( result.containsKey( Key.statusCode ) ).isFalse();
		assertThat( result.containsKey( Key.headers ) ).isFalse();
		assertThat( result.containsKey( Key.cookies ) ).isFalse();
	}

	@DisplayName( "Test raw mode passes through a non-struct return value untouched" )
	@Test
	public void testRawModeReturnsNonStructValues() {
		LambdaRunner runner = new LambdaRunner( fixture( "responseRawPlain" ), true, null, "raw" );

		assertThat( runner.handleRequest( eventWith( "text", true ), new TestContext() ) ).isEqualTo( "hello" );
		IStruct result = ( IStruct ) runner.handleRequest( eventWith(), new TestContext() );
		assertThat( result.get( Key.of( "id" ) ) ).isEqualTo( 1 );
		assertThat( result.containsKey( Key.statusCode ) ).isFalse();
	}

	@DisplayName( "Test a handled error in http mode defaults to 500 and the onError body, instead of a 200" )
	@Test
	public void testHandledErrorDefaultsTo500InHttpMode() {
		LambdaRunner	runner		= new LambdaRunner( fixture( "responseHooks" ), true );
		IStruct			response	= ( IStruct ) runner.handleRequest( eventWith( "fail", true ), new TestContext() );

		assertThat( response.getAsInteger( Key.statusCode ) ).isEqualTo( 500 );
		IStruct body = ( IStruct ) response.get( Key.body );
		assertThat( body.get( Key.of( "ok" ) ) ).isEqualTo( false );
		assertThat( body.get( Key.of( "error" ) ) ).isEqualTo( "boom" );
	}

	@DisplayName( "Test onError can override the default 500 status through the response struct" )
	@Test
	public void testOnErrorCanOverrideTheStatus() {
		LambdaRunner	runner		= new LambdaRunner( fixture( "responseHooks" ), true );
		IStruct			response	= ( IStruct ) runner.handleRequest( eventWith( "fail", true, "status", 404 ), new TestContext() );

		assertThat( response.getAsInteger( Key.statusCode ) ).isEqualTo( 404 );
	}

	@DisplayName( "Test a handled error in raw mode returns the onError body as a successful invocation" )
	@Test
	public void testHandledErrorInRawMode() {
		LambdaRunner	runner	= new LambdaRunner( fixture( "responseHooks" ), true, null, "raw" );
		IStruct			result	= ( IStruct ) runner.handleRequest( eventWith( "fail", true ), new TestContext() );

		assertThat( result.get( Key.of( "ok" ) ) ).isEqualTo( false );
		assertThat( result.get( Key.of( "error" ) ) ).isEqualTo( "boom" );
		assertThat( result.containsKey( Key.statusCode ) ).isFalse();
	}

	@DisplayName( "Test an unhandled error (no onError) still fails the invocation, in both modes" )
	@Test
	public void testUnhandledErrorStillThrows() {
		for ( String mode : new String[] { "http", "raw" } ) {
			LambdaRunner runner = new LambdaRunner( fixture( "responseNoOnError" ), true, null, mode );
			assertThrows( RuntimeException.class, () -> runner.handleRequest( eventWith(), new TestContext() ) );
		}
	}

	@DisplayName( "Test an invalid response mode hard-aborts cold start" )
	@Test
	public void testInvalidResponseModeHardAborts() {
		BoxRuntimeException thrown = assertThrows(
		    BoxRuntimeException.class,
		    () -> new LambdaRunner( fixture( "responseHooks" ), true, null, "row" )
		);
		assertThat( thrown.getMessage() ).contains( "BOXLANG_RESPONSE_MODE" );
		assertThat( thrown.getMessage() ).contains( "row" );
	}

	@DisplayName( "Test onRequestStart receives the response struct, so it can set the status and body before the handler runs" )
	@Test
	public void testOnRequestStartCanWriteTheResponse() {
		LambdaRunner	runner		= new LambdaRunner( fixture( "responseStartHook" ), true );
		IStruct			response	= ( IStruct ) runner.handleRequest( eventWith(), new TestContext() );

		assertThat( response.getAsInteger( Key.statusCode ) ).isEqualTo( 202 );
		assertThat( response.get( Key.body ) ).isEqualTo( "from-start" );
	}

	@DisplayName( "Test onRequestStart can write the response body in raw mode" )
	@Test
	public void testOnRequestStartCanWriteTheResponseInRawMode() {
		LambdaRunner runner = new LambdaRunner( fixture( "responseStartHook" ), true, null, "raw" );

		assertThat( runner.handleRequest( eventWith(), new TestContext() ) ).isEqualTo( "from-start" );
	}

	@DisplayName( "Test onAbort receives the response struct" )
	@Test
	public void testOnAbortReceivesTheResponse() {
		LambdaRunner	runner	= new LambdaRunner( fixture( "responseAbortHook" ), true, null, "raw" );
		IStruct			result	= ( IStruct ) runner.handleRequest( eventWith(), new TestContext() );

		assertThat( result.get( Key.of( "aborted" ) ) ).isEqualTo( true );
	}
}
