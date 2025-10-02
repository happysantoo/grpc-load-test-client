@echo off
setlocal EnableDelayedExpansion

REM gRPC Load Test Client Runner Script - Windows Version
REM This script provides easy execution of the load test client with common configurations

REM Default values
set HOST=localhost
set PORT=8080
set TPS=100
set DURATION=60
set CONCURRENCY=1000
set METHOD=Echo
set OUTPUT=console

REM Colors (using echo with ANSI escape codes - works in Windows 10+)
set RED=[91m
set GREEN=[92m
set YELLOW=[93m
set NC=[0m

REM Function to print usage
:usage
echo Usage: %0 [OPTIONS]
echo.
echo Options:
echo   -h, --host HOST          Target gRPC server host (default: localhost)
echo   -p, --port PORT          Target gRPC server port (default: 8080)
echo   -t, --tps TPS            Target transactions per second (default: 100)
echo   -d, --duration SECONDS   Test duration in seconds (default: 60)
echo   -c, --concurrency NUM    Maximum concurrent requests (default: 1000)
echo   -m, --method METHOD      gRPC method (Echo, ComputeHash, HealthCheck) (default: Echo)
echo   -o, --output FORMAT      Output format (console, json, csv) (default: console)
echo   -f, --file FILE          Output file path
echo   --tls                    Use TLS connection
echo   --config FILE            Use configuration file
echo   --verbose                Enable verbose logging
echo   --help                   Show this help message
echo.
echo Examples:
echo   %0 --host myservice.com --port 443 --tls --tps 200
echo   %0 --tps 500 --duration 300 --output json --file results.json
echo   %0 --config my-config.yaml
exit /b 1

REM Function to check if Java 21+ is available
:check_java
java -version >nul 2>&1
if errorlevel 1 (
    echo %RED%Error: Java is not installed or not in PATH%NC%
    exit /b 1
)

REM Get Java version
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION_STRING=%%i
    goto :parse_version
)

:parse_version
REM Remove quotes and extract major version
set JAVA_VERSION_STRING=!JAVA_VERSION_STRING:"=!
for /f "tokens=1 delims=." %%a in ("!JAVA_VERSION_STRING!") do set JAVA_MAJOR=%%a
if !JAVA_MAJOR! LSS 21 (
    echo %RED%Error: Java 21 or higher is required. Found version: !JAVA_MAJOR!%NC%
    exit /b 1
)

echo %GREEN%✓ Java !JAVA_MAJOR! detected%NC%
goto :eof

REM Function to build the project
:build_project
echo %YELLOW%Building project...%NC%
gradlew.bat build > build.log 2>&1
if errorlevel 1 (
    echo %RED%Error: Build failed. Check build.log for details%NC%
    exit /b 1
)
echo %GREEN%✓ Build successful%NC%
goto :eof

REM Initialize variables
set ARGS=
set USE_CONFIG=false
set CONFIG_FILE=

REM Parse command line arguments
:parse_args
if "%1"=="" goto end_parse

if "%1"=="-h" (
    set HOST=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--host" (
    set HOST=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-p" (
    set PORT=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--port" (
    set PORT=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-t" (
    set TPS=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--tps" (
    set TPS=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-d" (
    set DURATION=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--duration" (
    set DURATION=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-c" (
    set CONCURRENCY=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--concurrency" (
    set CONCURRENCY=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-m" (
    set METHOD=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--method" (
    set METHOD=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-o" (
    set OUTPUT=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--output" (
    set OUTPUT=%2
    shift
    shift
    goto parse_args
)
if "%1"=="-f" (
    set ARGS=!ARGS! --output-file %2
    shift
    shift
    goto parse_args
)
if "%1"=="--file" (
    set ARGS=!ARGS! --output-file %2
    shift
    shift
    goto parse_args
)
if "%1"=="--tls" (
    set ARGS=!ARGS! --tls
    shift
    goto parse_args
)
if "%1"=="--config" (
    set USE_CONFIG=true
    set CONFIG_FILE=%2
    shift
    shift
    goto parse_args
)
if "%1"=="--verbose" (
    set ARGS=!ARGS! --verbose
    shift
    goto parse_args
)
if "%1"=="--help" (
    goto usage
)

echo %RED%Unknown option: %1%NC%
goto usage

:end_parse

REM Check prerequisites
echo %YELLOW%Checking prerequisites...%NC%
call :check_java

REM Build project
call :build_project

REM Prepare arguments
if "%USE_CONFIG%"=="true" (
    if not exist "%CONFIG_FILE%" (
        echo %RED%Error: Configuration file not found: %CONFIG_FILE%%NC%
        exit /b 1
    )
    set ARGS=--config %CONFIG_FILE% !ARGS!
) else (
    set ARGS=--host %HOST% --port %PORT% --tps %TPS% --duration %DURATION% --concurrency %CONCURRENCY% --method %METHOD% --output-format %OUTPUT% !ARGS!
)

REM Print test configuration
echo %YELLOW%Starting gRPC Load Test with configuration:%NC%
if "%USE_CONFIG%"=="true" (
    echo   Configuration file: %CONFIG_FILE%
) else (
    echo   Target: %HOST%:%PORT%
    echo   TPS: %TPS%
    echo   Duration: %DURATION%s
    echo   Concurrency: %CONCURRENCY%
    echo   Method: %METHOD%
    echo   Output: %OUTPUT%
)
echo.

REM Run the load test
echo %GREEN%Running load test...%NC%
gradlew.bat run --args="!ARGS!" --quiet

if errorlevel 1 (
    echo %RED%Load test failed%NC%
    exit /b 1
)

echo %GREEN%✓ Load test completed%NC%