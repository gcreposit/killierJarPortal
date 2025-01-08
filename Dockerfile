# Use a lightweight base image with JDK for Java 17
FROM openjdk:17-jdk-slim

# Add author label
LABEL authors="SameerCR7"

# Install necessary dependencies including OpenSSL
RUN apt-get update && apt-get install -y --no-install-recommends \
    openssl \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Create a volume for application data (optional for persisting data)
VOLUME /tmp

# Set the working directory inside the container
WORKDIR /app

# Expose the application port
EXPOSE 5050:5050


# Copy the correct JAR file into the container
COPY target/JarStatusPortal-0.0.1-SNAPSHOT.jar /app/jar_status_portal.jar

# Copy the JSON file into the container
COPY src/main/resources/jalshakti-1734933826605-883dbf306dbe.json /app/resources/

# Specify the command to run the Spring Boot application
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "/app/jar_status_portal.jar"]
