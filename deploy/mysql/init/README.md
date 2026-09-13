# MySQL 初始化脚本

MySQL 容器首次启动（数据卷为空）时会按文件名顺序自动执行本目录下的 `.sql` 文件。

## 生成全量数据库导出（仓库内没有全量建表 SQL，必须先执行）

先启动本机 MySQL，然后在 PowerShell 执行（用 cmd /c 避免 UTF-16 编码问题）：

```powershell
cmd /c "mysqldump -h127.0.0.1 -uroot -p1115 --databases lovemap --single-transaction --default-character-set=utf8mb4 --add-drop-database > E:\java\program\LoveOfUs\deploy\mysql\init\01_lovemap.sql"
```

生成后文件名为 `01_lovemap.sql`（01 前缀保证最先执行）。
注意：`sql/migrate/V1~V9` 是增量脚本，依赖已存在的基础表，不能替代全量导出。
