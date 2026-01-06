@echo off
REM 直接使用 PostgreSQL 命令执行 SQL 脚本
REM 需要在环境中配置 PGPASSWORD 或使用 .pgpass 文件

set PGHOST=localhost
set PGPORT=5432
set PGUSER=postgres
set PGPASSWORD=postgres
set PGDATABASE=postgres

REM 查找 psql 可执行文件
for /f "delims=" %%i in ('where psql 2^>nul') do (
    set PSQL_PATH=%%i
    goto found_psql
)

REM 如果找不到，尝试常见的安装路径
if exist "C:\Program Files\PostgreSQL\15\bin\psql.exe" (
    set PSQL_PATH=C:\Program Files\PostgreSQL\15\bin\psql.exe
) else if exist "C:\Program Files\PostgreSQL\14\bin\psql.exe" (
    set PSQL_PATH=C:\Program Files\PostgreSQL\14\bin\psql.exe
) else if exist "C:\Program Files\PostgreSQL\13\bin\psql.exe" (
    set PSQL_PATH=C:\Program Files\PostgreSQL\13\bin\psql.exe
) else (
    echo PostgreSQL psql 工具未找到！
    exit /b 1
)

:found_psql
echo 使用 psql: %PSQL_PATH%
echo 执行初始化脚本...

"%PSQL_PATH%" -h %PGHOST% -p %PGPORT% -U %PGUSER% -d %PGDATABASE% -f src/main/resources/db/migration/V1_0__init_task_config.sql

if %errorlevel% equ 0 (
    echo ✓ 数据库初始化成功！
) else (
    echo ✗ 数据库初始化失败！
    exit /b 1
)
