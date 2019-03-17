@echo off
rem -------------------------------------------------------------------------
rem dcmdump  Launcher
rem -------------------------------------------------------------------------

if not "%ECHO%" == ""  echo %ECHO%
if "%OS%" == "Windows_NT"  setlocal

set MAIN_MODULE=org.dcm4che.tool.dcmdump
set MAIN_JAR=dcm4che-tool-dcmdump-${project.version}.jar

set DIRNAME=.\
if "%OS%" == "Windows_NT" set DIRNAME=%~dp0%

rem Read all command line arguments

set ARGS=
:loop
if [%1] == [] goto end
        set ARGS=%ARGS% %1
        shift
        goto loop
:end

if not "%DCM4CHE_HOME%" == "" goto HAVE_DCM4CHE_HOME

set DCM4CHE_HOME=%DIRNAME%..

:HAVE_DCM4CHE_HOME

if not "%JAVA_HOME%" == "" goto HAVE_JAVA_HOME

set JAVA=java

goto SKIP_SET_JAVA_HOME

:HAVE_JAVA_HOME

set JAVA=%JAVA_HOME%\bin\java

:SKIP_SET_JAVA_HOME

set MP=%MP%;%DCM4CHE_HOME%\lib\%MAIN_JAR%
set MP=%MP%;%DCM4CHE_HOME%\lib\dcm4che-core-${project.version}.jar
set MP=%MP%;%DCM4CHE_HOME%\lib\picocli-${picocli.version}.jar

"%JAVA%" %JAVA_OPTS% -p "%MP%" -m %MAIN_MODULE% %ARGS%
