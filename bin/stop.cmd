@echo off
rem 本地 Windows 自测用：按启动 jar 对应的控制台窗口标题终止进程。
rem 生产环境请使用 bin\stop.sh（Linux，支持优雅 TERM + 超时 kill -9）。
setlocal

pushd "%~dp0.."

set "APP_JAR="
for %%f in (*.jar) do set "APP_JAR=%%f"
if not defined APP_JAR (
    echo [ERROR] 当前目录下未找到启动 jar
    popd
    exit /b 1
)

echo stopping %APP_JAR% ...
taskkill /F /FI "WINDOWTITLE eq %APP_JAR%*" >nul 2>&1
if errorlevel 1 (
    echo [WARN] 未找到运行中的 %APP_JAR% 进程
) else (
    echo stopped %APP_JAR%
)

popd
endlocal
