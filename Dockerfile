FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN mkdir -p out && javac -cp "lib/*" -d out $(find src -name "*.java")

EXPOSE 8080

CMD ["sh", "-c", "java -cp 'out:lib/*' kalvi.transport.web.WebApp --demo --no-browser --port ${PORT:-8080}"]