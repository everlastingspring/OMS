FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/order-service.jar app.jar
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
