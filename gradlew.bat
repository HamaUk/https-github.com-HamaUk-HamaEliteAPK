@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto preExecute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto preExecute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

@rem Prefer JDK 17-21 when JAVA_HOME / PATH points to JDK 25+ (Kotlin DSL script compiler).
:preExecute
if defined GRADLE_JAVA_HOME (
    set "JAVA_HOME=%GRADLE_JAVA_HOME:"=%"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    if exist "%JAVA_EXE%" goto execute
    echo ERROR: GRADLE_JAVA_HOME is invalid: %GRADLE_JAVA_HOME%
    goto fail
)
@rem Parse major version from first line (java/openjdk version "MAJOR...") — works with stderr and avoids findstr quoting bugs
set "JVL="
for /f "tokens=* usebackq" %%i in (`"%JAVA_EXE%" -version 2^>^&1`) do (
    set "JVL=%%i"
    goto gradle_jv_got
)
:gradle_jv_got
if "%JVL%"=="" goto execute
set "JVN="
for /f "tokens=2 delims=^"" %%a in ("%JVL%") do set "JVN=%%a"
if "%JVN%"=="" goto execute
set "JMAJ="
for /f "tokens=1 delims=." %%m in ("%JVN%") do set "JMAJ=%%m"
if "%JMAJ%"=="" goto execute
if %JMAJ% LSS 25 goto execute
call :useSupportedJdkForGradle
if errorlevel 1 goto fail
goto execute

:useSupportedJdkForGradle
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    echo Gradle: using Android Studio JBR at %JAVA_HOME% ^(not JDK 25+^).
    exit /b 0
)
if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    echo Gradle: using Android Studio JBR ^(user install^).
    exit /b 0
)
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        set "JAVA_EXE=%%D\bin\java.exe"
        echo Gradle: using %%D
        exit /b 0
    )
)
for /d %%D in ("%ProgramFiles%\Microsoft\jdk-21*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        set "JAVA_EXE=%%D\bin\java.exe"
        echo Gradle: using %%D
        exit /b 0
    )
)
for /d %%D in ("%ProgramFiles%\Java\jdk-21*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        set "JAVA_EXE=%%D\bin\java.exe"
        echo Gradle: using %%D
        exit /b 0
    )
)
for /d %%D in ("%ProgramFiles%\Java\jdk-17*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        set "JAVA_EXE=%%D\bin\java.exe"
        echo Gradle: using %%D
        exit /b 0
    )
)
echo.
echo ERROR: JAVA_HOME points to JDK 25 or newer. Gradle's Kotlin DSL cannot use that JVM yet.
echo Install JDK 21, or set GRADLE_JAVA_HOME to a JDK 17-21 folder, or install Android Studio ^(JBR^).
echo.
exit /b 1

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
