# systemd 部署方案（非 Docker）
> 中文名：systemd 部署方案

## 1. 目标机器准备
- OS: Linux（systemd）
- JDK: 25
- PostgreSQL: 可访问
- 目录约定:
  - 应用目录: `/opt/file-batch-processor`
  - 环境变量: `/etc/file-batch-processor/file-batch-processor.env`

## 2. 构建与发布
在构建机执行:
```bash
./mvnw -DskipTests clean package
```
将产物上传到目标机:
```bash
scp target/*.jar <host>:/opt/file-batch-processor/file-batch-processor.jar
```

## 3. 安装服务
在项目根目录执行:
```bash
bash deploy/systemd/install-systemd.sh
```
然后修改环境变量文件:
```bash
sudo vi /etc/file-batch-processor/file-batch-processor.env
```

## 4. 启停与开机自启
```bash
sudo systemctl start file-batch-processor
sudo systemctl stop file-batch-processor
sudo systemctl restart file-batch-processor
sudo systemctl status file-batch-processor
sudo systemctl enable file-batch-processor
```

## 5. 日志与排障
```bash
journalctl -u file-batch-processor -f
journalctl -u file-batch-processor --since "1 hour ago"
```

## 6. 发布回滚建议
1. 保留最近 N 个版本 jar（例如 `/opt/file-batch-processor/releases/`）。
2. 切换软链接 `current.jar` 到旧版本。
3. `sudo systemctl restart file-batch-processor`。
4. 验证 `/actuator/health` 与核心作业执行结果。
