#!/bin/bash

# Quote Management Service Docker Build Script

echo "🐋 Building Quote Management Service Docker Image..."

# Set variables
IMAGE_NAME="quote-management-service"
IMAGE_TAG="1.0.0"
FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"

# Create uploads directory if it doesn't exist
mkdir -p uploads/attachments

# Build the Docker image
echo "📦 Building Docker image: ${FULL_IMAGE_NAME}"
docker build -t ${FULL_IMAGE_NAME} -t ${IMAGE_NAME}:latest .

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully!"
    echo "📊 Image details:"
    docker images | grep ${IMAGE_NAME}
    
    echo ""
    echo "🚀 To run the container, use:"
    echo "   docker-compose up -d"
    echo "   or"
    echo "   docker run -p 8088:8088 -e TMFORUM_API_BASE_URL=your-api-url ${FULL_IMAGE_NAME}"
else
    echo "❌ Docker build failed!"
    exit 1
fi 