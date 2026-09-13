@echo off
setlocal
where mvn >nul 2>nul
if not errorlevel 1 (
  mvn %*
  exit /b %errorlevel%
)

set MAVEN_VERSION=3.9.11
if "%MAVEN_USER_HOME%"=="" (
  set MAVEN_CACHE=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
) else (
  set MAVEN_CACHE=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%
)
set MAVEN_BIN=%MAVEN_CACHE%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd
set DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

if not exist "%MAVEN_BIN%" (
  if not exist "%MAVEN_CACHE%" mkdir "%MAVEN_CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%MAVEN_CACHE%\maven.zip'; Expand-Archive -Path '%MAVEN_CACHE%\maven.zip' -DestinationPath '%MAVEN_CACHE%' -Force; Remove-Item '%MAVEN_CACHE%\maven.zip'"
  if errorlevel 1 exit /b 1
)

call "%MAVEN_BIN%" %*
exit /b %errorlevel%
