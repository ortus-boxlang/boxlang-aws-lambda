#!/bin/bash

# BoxLang Lambda Performance Testing Script

echo "🚀 BoxLang Lambda Performance Testing"
echo "======================================"

# Test different payload sizes
PAYLOADS=(
    '{"test": "small"}'
    '{"test": "medium", "data": "'$(head -c 1000 /dev/zero | tr '\0' 'x')'"}'
    '{"test": "large", "data": "'$(head -c 10000 /dev/zero | tr '\0' 'x')'"}'
)

EVENTS=(
    "workbench/sampleEvents/api.json"
    "workbench/sampleEvents/event.json"
    "workbench/sampleEvents/health.json"
    "workbench/sampleEvents/large-payload.json"
)

# Build the Lambda first
echo "📦 Building Lambda..."
if ! ./gradlew shadowJar buildMainZip; then
    echo "❌ Build failed! Please fix build errors first."
    exit 1
fi

# Check if SAM CLI is available
if ! command -v sam &> /dev/null; then
    echo "❌ AWS SAM CLI not found. Please install SAM CLI first:"
    echo "   https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html"
    exit 1
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. SAM Local requires Docker to be installed and running."
    echo "   Please start Docker and try again."
    echo ""
    echo "🔧 Alternative: Run unit tests instead:"
    echo "   ./gradlew test"
    exit 1
fi

# Warm up test
echo "🔥 Warming up Lambda container..."
if ! sam local invoke bxFunction --event=workbench/sampleEvents/api.json > /dev/null 2>&1; then
    echo "❌ Lambda warm-up failed. Check your SAM template and build artifacts."
    echo "   Make sure template.yml exists and CodeUri points to the correct build artifact."
    exit 1
fi

# Performance tests
for event in "${EVENTS[@]}"; do
    echo ""
    echo "📊 Testing with event: $event"
    echo "-----------------------------------"

    # Check if event file exists
    if [ ! -f "$event" ]; then
        echo "⚠️  Event file $event not found, skipping..."
        continue
    fi

    # Run multiple iterations
    times=()
    failed_runs=0
    for i in {1..5}; do
        start_time=$(date +%s%N)
        if sam local invoke bxFunction --event="$event" > /dev/null 2>&1; then
            end_time=$(date +%s%N)
            duration=$((($end_time - $start_time) / 1000000)) # Convert to milliseconds
            times+=($duration)
            echo "  Run $i: ${duration}ms"
        else
            echo "  Run $i: FAILED"
            ((failed_runs++))
        fi
    done

    # Calculate average if we have successful runs
    if [ ${#times[@]} -gt 0 ]; then
        sum=0
        for time in "${times[@]}"; do
            ((sum += time))
        done
        avg=$((sum / ${#times[@]}))
        echo "  Average: ${avg}ms (${#times[@]}/${#times[@]:$failed_runs} successful runs)"
    else
        echo "  ❌ All runs failed for $event"
    fi
done

# Memory usage test
echo ""
echo "💾 Memory Usage Test"
echo "--------------------"
# This would require custom Lambda code to report memory usage
# For now, we'll show how to structure the test

echo "To test memory usage:"
echo "1. Add memory monitoring to your Lambda.bx"
echo "2. Set BOXLANG_LAMBDA_DEBUGMODE=true"
echo "3. Check CloudWatch logs for memory metrics"

echo ""
echo "🎯 Performance Recommendations:"
echo "1. Use class-level variables for expensive resources"
echo "2. Implement connection pooling for databases"
echo "3. Cache compiled BoxLang classes (now implemented)"
echo "4. Use appropriate Lambda memory allocation"
echo "5. Consider provisioned concurrency for predictable workloads"

echo ""
echo "🔧 Alternative Testing (without Docker):"
echo "Run unit tests to verify caching improvements:"
echo "  ./gradlew test --info"
echo ""
echo "Test with actual AWS Lambda:"
echo "1. Deploy: aws lambda create-function --function-name bx-test --runtime java21 --role <role-arn> --handler ortus.boxlang.runtime.aws.LambdaRunner::handleRequest --zip-file fileb://build/distributions/boxlang-aws-lambda-1.5.0-snapshot.zip"
echo "2. Invoke: aws lambda invoke --function-name bx-test --payload file://workbench/sampleEvents/api.json response.json"

echo ""
echo "✅ Performance testing complete!"
