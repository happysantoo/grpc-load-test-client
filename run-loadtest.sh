#!/bin/bash

# gRPC Load Test Client Runner Script
# This script provides easy execution of the load test client with common configurations

set -e

# Default values
HOST="localhost"
PORT="8080"
TPS="100"
DURATION="60"
CONCURRENCY="1000"
METHOD="Echo"
OUTPUT="console"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --host HOST          Target gRPC server host (default: localhost)"
    echo "  -p, --port PORT          Target gRPC server port (default: 8080)"
    echo "  -t, --tps TPS            Target transactions per second (default: 100)"
    echo "  -d, --duration SECONDS   Test duration in seconds (default: 60)"
    echo "  -c, --concurrency NUM    Maximum concurrent requests (default: 1000)"
    echo "  -m, --method METHOD      gRPC method (Echo, ComputeHash, HealthCheck) (default: Echo)"
    echo "  -o, --output FORMAT      Output format (console, json, csv) (default: console)"
    echo "  -f, --file FILE          Output file path"
    echo "  --tls                    Use TLS connection"
    echo "  --config FILE            Use configuration file"
    echo "  --verbose                Enable verbose logging"
    echo "  --help                   Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --host myservice.com --port 443 --tls --tps 200"
    echo "  $0 --tps 500 --duration 300 --output json --file results.json"
    echo "  $0 --config my-config.yaml"
    exit 1
}

# Function to check if Java 21+ is available
check_java() {
    if ! command -v java &> /dev/null; then
        echo -e "${RED}Error: Java is not installed or not in PATH${NC}"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | grep "openjdk version" | awk '{print $3}' | tr -d '"' | cut -d'.' -f1)
    if [ -z "$JAVA_VERSION" ]; then
        JAVA_VERSION=$(java -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d '"' | cut -d'.' -f1)
    fi
    
    if [ "$JAVA_VERSION" -lt "21" ]; then
        echo -e "${RED}Error: Java 21 or higher is required. Found version: $JAVA_VERSION${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Java $JAVA_VERSION detected${NC}"
}

# Function to build the project
build_project() {
    echo -e "${YELLOW}Building project...${NC}"
    if ! ./gradlew build > build.log 2>&1; then
        echo -e "${RED}Error: Build failed. Check build.log for details${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Build successful${NC}"
}

# Parse command line arguments
ARGS=""
USE_CONFIG=false
CONFIG_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--host)
            HOST="$2"
            shift 2
            ;;
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        -t|--tps)
            TPS="$2"
            shift 2
            ;;
        -d|--duration)
            DURATION="$2"
            shift 2
            ;;
        -c|--concurrency)
            CONCURRENCY="$2"
            shift 2
            ;;
        -m|--method)
            METHOD="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT="$2"
            shift 2
            ;;
        -f|--file)
            ARGS="$ARGS --output-file $2"
            shift 2
            ;;
        --tls)
            ARGS="$ARGS --tls"
            shift
            ;;
        --config)
            USE_CONFIG=true
            CONFIG_FILE="$2"
            shift 2
            ;;
        --verbose)
            ARGS="$ARGS --verbose"
            shift
            ;;
        --help)
            usage
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            ;;
    esac
done

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"
check_java

# Build project
build_project

# Prepare arguments
if [ "$USE_CONFIG" = true ]; then
    if [ ! -f "$CONFIG_FILE" ]; then
        echo -e "${RED}Error: Configuration file not found: $CONFIG_FILE${NC}"
        exit 1
    fi
    ARGS="--config $CONFIG_FILE $ARGS"
else
    ARGS="--host $HOST --port $PORT --tps $TPS --duration $DURATION --concurrency $CONCURRENCY --method $METHOD --output-format $OUTPUT $ARGS"
fi

# Print test configuration
echo -e "${YELLOW}Starting gRPC Load Test with configuration:${NC}"
if [ "$USE_CONFIG" = true ]; then
    echo "  Configuration file: $CONFIG_FILE"
else
    echo "  Target: $HOST:$PORT"
    echo "  TPS: $TPS"
    echo "  Duration: ${DURATION}s"
    echo "  Concurrency: $CONCURRENCY"
    echo "  Method: $METHOD"
    echo "  Output: $OUTPUT"
fi
echo ""

# Run the load test
echo -e "${GREEN}Running load test...${NC}"
./gradlew run --args="$ARGS" --quiet

echo -e "${GREEN}✓ Load test completed${NC}"