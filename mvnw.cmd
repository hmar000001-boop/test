@echo off
setlocal
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
set "VERSION=%MAVEN_VERSION%"
if "%VERSION%"=="" set "VERSION=3.10.0"
if "%VERSION%"=="3.10.0" (
  set "EXPECTED_SHA512=22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337"
) else if "%VERSION%"=="3.9.16" (
  set "EXPECTED_SHA512=ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3"
) else (
  if "%MAVEN_SHA512%"=="" (
    echo Custom MAVEN_VERSION requires MAVEN_SHA512; refusing to install an unverified Maven archive. 1>&2
    exit /b 1
  )
  set "EXPECTED_SHA512=%MAVEN_SHA512%"
)
set "BASE=%USERPROFILE%\.cache\coala-maven"
set "DIST=%BASE%\apache-maven-%VERSION%"
if exist "%DIST%\bin\mvn.cmd" goto runmaven
if not exist "%BASE%" mkdir "%BASE%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; $base='%BASE%'; $version='%VERSION%'; $expected='%EXPECTED_SHA512%'; $archive=Join-Path $base ('apache-maven-' + $version + '-bin.zip'); $urls=@('https://dlcdn.apache.org/maven/maven-3/' + $version + '/binaries/apache-maven-' + $version + '-bin.zip','https://archive.apache.org/dist/maven/maven-3/' + $version + '/binaries/apache-maven-' + $version + '-bin.zip'); $downloaded=$false; foreach($url in $urls){ Remove-Item $archive -Force -ErrorAction SilentlyContinue; try { Invoke-WebRequest -Uri $url -OutFile $archive; $downloaded=$true; break } catch {} }; if(!$downloaded -or !(Test-Path $archive)){ throw 'Unable to download Maven. Install Maven and retry.' }; $actual=(Get-FileHash -Path $archive -Algorithm SHA512).Hash.ToLowerInvariant(); if($actual -ne $expected.ToLowerInvariant()){ Remove-Item $archive -Force; throw 'Maven archive SHA-512 mismatch; refusing to extract.' }; Expand-Archive -Path $archive -DestinationPath $base -Force; Remove-Item $archive -Force"
if errorlevel 1 exit /b 1
:runmaven
"%DIST%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
