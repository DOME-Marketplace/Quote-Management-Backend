@echo off
echo Starting Quote Management Service locally...
echo.

REM Set JAVA_HOME to use the correct Java version
set JAVA_HOME=C:\Program Files\Java\jdk-18.0.2.1
set PATH=%JAVA_HOME%\bin;%PATH%

echo Using Java version:
java -version
echo.

echo Building and running the application...
mvn clean spring-boot:run -Dspring-boot.run.profiles=local

pause 