@echo off
REM Quote Management Service Docker Build Script (Windows)

echo 🐋 Building Quote Management Service Docker Image...

REM Set variables
set IMAGE_NAME=quote-management-service
set IMAGE_TAG=1.0.0
set FULL_IMAGE_NAME=%IMAGE_NAME%:%IMAGE_TAG%

REM Create uploads directory if it doesn't exist
if not exist "uploads\attachments" mkdir uploads\attachments

REM Build the Docker image
echo 📦 Building Docker image: %FULL_IMAGE_NAME%
docker build -t %FULL_IMAGE_NAME% -t %IMAGE_NAME%:latest .

REM Check if build was successful
if %ERRORLEVEL% equ 0 (
    echo ✅ Docker image built successfully!
    echo 📊 Image details:
    docker images | findstr %IMAGE_NAME%
    
    echo.
    echo 🚀 To run the container, use:
    echo    docker-compose up -d
    echo    or
    echo    docker run -p 8080:8080 -e TMFORUM_API_BASE_URL=your-api-url %FULL_IMAGE_NAME%
) else (
    echo ❌ Docker build failed!
    exit /b 1
) 