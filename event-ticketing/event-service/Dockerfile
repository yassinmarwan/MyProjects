# Use the exact JDK specified in the rubric
FROM eclipse-temurin:25.0.2_10-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled JAR file from the target directory into the container
# (Assuming your pom.xml generates a standard jar)
COPY target/*.jar app.jar

# Expose port 8080 (the internal port the Spring Boot app runs on)
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]