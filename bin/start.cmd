@echo off
rem 本地 Windows 自测用：在新窗口启动当前部署单元。
rem 生产环境请使用 bin\start.sh（Linux）；两者同样依赖 -Dloader.path=libs,conf。
setlocal

pushd "%~dp0.."

set "APP_JAR="
for %%f in (*.jar) do set "APP_JAR=%%f"
if not defined APP_JAR (
    echo [ERROR] 当前目录下未找到启动 jar
    popd
    exit /b 1
)

if not exist logs mkdir logs

echo starting %APP_JAR% ...
start "%APP_JAR%" java -Dloader.path=libs,conf -jar "%APP_JAR%"
echo started %APP_JAR%，日志见 logs\ 目录

popd
endlocal
