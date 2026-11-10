# Build the existing Maven WAR using Java 17.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

# Run the WAR on the matching Jakarta/Tomcat 10.1 runtime.
FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /build/target/smart-complaint.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
ENV CATALINA_OPTS="-Xms128m -Xmx512m"
CMD ["catalina.sh", "run"]
