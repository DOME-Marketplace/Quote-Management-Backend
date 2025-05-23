# Use Maven image for building
FROM maven:3.9.4-eclipse-temurin-17 as build
WORKDIR /workspace/app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the built JAR file
COPY --from=build /workspace/app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Set active profile and run the application
ENTRYPOINT ["java", "-Dspring.profiles.active=local", "-jar", "/app.jar"] 