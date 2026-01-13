# backend/Dockerfile

# Build stage
FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built jar
COPY --from=build /app/build/libs/backend-0.0.1-SNAPSHOT.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]