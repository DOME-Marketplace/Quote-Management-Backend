# 🐋 Docker Deployment Guide

This guide explains how to build and run the Quote Management Backend service using Docker.

## 📋 Prerequisites

- Docker Desktop or Docker Engine
- Docker Compose (usually included with Docker Desktop)
- At least 2GB of free disk space
- Port 8080 available on your host machine

## 🚀 Quick Start

### Option 1: Using Docker Compose (Recommended)

1. **Clone and navigate to the project directory**
   ```bash
   cd Quote-Management-Backend
   ```

2. **Copy environment variables** (optional)
   ```bash
   cp env.example .env
   # Edit .env file with your TMForum API URL
   ```

3. **Build and run the service**
   ```bash
   docker-compose up -d
   ```

4. **Access the application**
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health Check: http://localhost:8080/actuator/health

### Option 2: Using Build Scripts

**For Linux/macOS:**
```bash
chmod +x docker-build.sh
./docker-build.sh
```

**For Windows:**
```cmd
docker-build.bat
```

### Option 3: Manual Docker Commands

1. **Build the image**
   ```bash
   docker build -t quote-management-service:latest .
   ```

2. **Run the container**
   ```bash
   docker run -d \
     --name quote-management \
     -p 8080:8080 \
     -e TMFORUM_API_BASE_URL=https://an-dhub-sbx.dome-project.eu/tmf-api \
     -v $(pwd)/uploads:/app/uploads \
     quote-management-service:latest
   ```

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TMFORUM_API_BASE_URL` | TMForum API endpoint | `https://an-dhub-sbx.dome-project.eu/tmf-api` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `local` |
| `LOG_LEVEL` | Logging level | `INFO` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m` |

### Available TMForum API URLs

- **AccessNode Dhub (Default)**: `https://an-dhub-sbx.dome-project.eu/tmf-api`
- **Dhub DEV**: `https://dome-dev.eng.it/tmf-api`
- **AccessNode Marketplace**: `https://tmf.dome-marketplace-sbx.org/tmf-api`

## 📊 Monitoring & Health Checks

### Health Check Endpoint
```bash
curl http://localhost:8080/actuator/health
```

### Container Logs
```bash
# Docker Compose
docker-compose logs -f quote-management-app

# Docker
docker logs -f quote-management
```

### Container Status
```bash
# Check running containers
docker ps

# Check container health
docker inspect quote-management-service --format='{{.State.Health.Status}}'
```

## 🛠️ Development

### Building for Development
```bash
# Build without cache
docker build --no-cache -t quote-management-service:dev .

# Build with specific target
docker build --target build -t quote-management-service:build-only .
```

### Debugging in Container
```bash
# Access container shell
docker exec -it quote-management /bin/sh

# Check Java process
docker exec quote-management ps aux | grep java

# Check disk space
docker exec quote-management df -h
```

## 🔧 Troubleshooting

### Common Issues

1. **Port 8080 already in use**
   ```bash
   # Change port in docker-compose.yml or use different port
   docker run -p 8081:8080 quote-management-service:latest
   ```

2. **TMForum API connection issues**
   ```bash
   # Check API URL is reachable
   curl -I https://an-dhub-sbx.dome-project.eu/tmf-api/quoteManagement/v4/quote

   # Update environment variable
   export TMFORUM_API_BASE_URL=your-correct-api-url
   ```

3. **Container won't start**
   ```bash
   # Check logs for errors
   docker logs quote-management

   # Check container resources
   docker stats quote-management
   ```

4. **File upload issues**
   ```bash
   # Ensure uploads directory exists and has correct permissions
   mkdir -p uploads/attachments
   chmod 755 uploads/attachments
   ```

### Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove containers and volumes
docker-compose down -v

# Remove images
docker rmi quote-management-service:latest

# Clean up everything
docker system prune -f
```

## 🌟 Production Considerations

1. **Use specific image tags** instead of `latest`
2. **Set resource limits** in docker-compose.yml
3. **Use secrets** for sensitive configuration
4. **Configure proper logging** with log rotation
5. **Set up monitoring** with Prometheus/Grafana
6. **Use HTTPS** with reverse proxy (nginx example included)

## 📞 Support

If you encounter issues:

1. Check the application logs
2. Verify TMForum API connectivity
3. Ensure Docker has sufficient resources
4. Check firewall/port configurations

For more information, see the main [README.md](README.md) file. 