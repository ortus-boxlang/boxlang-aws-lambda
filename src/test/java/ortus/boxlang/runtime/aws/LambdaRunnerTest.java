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

	@DisplayName( "Test a corrupt manifest.json falls back to the handlers/ directory scan instead of failing startup" )
	@Test
	public void testCorruptManifestFallsBackToDirectoryScan() throws IOException {
		Path			testPath	= Path.of( "src", "test", "resources", "corruptManifest" );
		LambdaRunner	runner		= new LambdaRunner( Path.of( testPath.toString(), "Lambda.bx" ), true );

		// manifest.json is invalid JSON; the handlers/Foo.bx directory scan should still
		// have registered "foo" as a fallback, rather than the constructor throwing
		assertThat( runner.getHandlerRoutes() ).containsKey( "foo" );
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

		var event = new HashMap<String, Object>();
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

}
