-- PostgreSQL 数据库初始化脚本
-- 用于 Docker 容器启动时的数据库初始化

-- 设置时区
SET timezone = 'Asia/Shanghai';

-- 创建扩展（如果需要）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 创建应用用户（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles 
        WHERE rolname = 'filebatch'
    ) THEN
        CREATE ROLE filebatch WITH LOGIN PASSWORD 'filebatch';
    END IF;
END
$$;

-- 授权数据库
GRANT ALL PRIVILEGES ON DATABASE qrtz TO filebatch;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO filebatch;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO filebatch;

-- 设置默认权限
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO filebatch;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO filebatch;

COMMIT;
