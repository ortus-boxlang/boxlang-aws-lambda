#!/bin/bash

# BoxLang Lambda Simple Testing Script (No Docker Required)

echo "🧪 BoxLang Lambda Simple Testing"
echo "================================="

# Build the Lambda first
echo "📦 Building Lambda..."
if ! ./gradlew shadowJar buildMainZip; then
    echo "❌ Build failed! Please fix build errors first."
    exit 1
fi

# Run unit tests to verify functionality and caching
echo ""
echo "🔬 Running Unit Tests..."
echo "------------------------"
./gradlew test --info

# Check build artifacts
echo ""
echo "📦 Build Artifacts:"
echo "-------------------"
ls -la build/distributions/*.zip | head -5

echo ""
echo "📋 Sample Events Available:"
echo "----------------------------"
ls -la workbench/sampleEvents/

echo ""
echo "🎯 Sample Event Contents:"
echo "-------------------------"
echo "📄 api.json:"
head -10 workbench/sampleEvents/api.json
echo ""
echo "📄 event.json:"
head -10 workbench/sampleEvents/event.json

echo ""
echo "✨ Ready for deployment!"
echo ""
echo "🚀 Next Steps:"
echo "1. Deploy to AWS Lambda using the ZIP file: build/distributions/boxlang-aws-lambda-*.zip"
echo "2. Set handler to: ortus.boxlang.runtime.aws.LambdaRunner::handleRequest"
echo "3. Set runtime to: java21"
echo "4. Test with any of the sample events in workbench/sampleEvents/"
echo ""
echo "🔧 For local testing with Docker, use: ./workbench/performance-test.sh"
