# Quote Management Service

A microservice for managing quote requests in the DOME Marketplace. This service handles the creation, updating, and management of quote requests between customers and providers.

## Features

- Quote request creation and management
- Integration with TMForum APIs
- Real-time quote status updates
- Quote negotiation support
- Document management for PDF quotes
- Provider and customer dashboards

## Technical Stack

- Java 17
- Spring Boot 3.2.3
- PostgreSQL
- Docker
- TMForum APIs

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker (for containerization)
- PostgreSQL database

## Building the Project

```bash
# Clone the repository
git clone [repository-url]

# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run
```

## Docker Deployment

```bash
# Build the Docker image
docker build -t quote-management-service .

# Run the container
docker run -p 8080:8080 quote-management-service
```

## API Documentation

Once the application is running, you can access the API documentation at:
```
http://localhost:8080/swagger-ui.html
```

## Configuration

The application can be configured using environment variables or application.properties:

```properties
# Server Configuration
server.port=8080

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/quote_management
spring.datasource.username=postgres
spring.datasource.password=postgres

# TMForum API Configuration
tmforum.api.base-url=http://your-tmforum-api-url
```

## License

This project is licensed under the terms of the LICENSE file included in the repository. 