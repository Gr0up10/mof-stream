FROM openjdk:11.0

COPY target/scala-2.13/mof-stream-assembly-0.1.jar app.jar
ENTRYPOINT java -jar app.jar