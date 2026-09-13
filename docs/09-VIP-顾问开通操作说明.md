# VIP 会员：顾问收款后手动开通

## 一、开通流程

1. 用户在 App「VIP 会员」页选择档位，点「邮箱」付款并提交订单（`POST /api/vip/orders`）。
   此时只登记一张**待开通**订单，用户不会立刻变成 VIP。
2. 下单成功后，系统**自动发一封开通申请邮件**给专属顾问 `xjl20041115@126.com`，
   邮件里含用户昵称、档位、订单号和可直接执行的**开通命令**（见第二节）。
3. 顾问确认收到款后，执行邮件里的开通命令，或先查待开通订单再批量开通（见第三节），
   订单置为**已开通**，用户立即获得权益。

> 一个用户同时只保留一张待开通订单：用户再次下单时，旧的待开通订单会自动置为已取消。
> VIP 归属情侣组，付款人下单即可，伴侣无需单独下单。

## 二、顾问通知邮件

订单创建成功后自动发信，异步发送（失败只记日志，不影响用户下单结果）：

| 项 | 值 |
| --- | --- |
| 收件人 | `xjl20041115@126.com`（`VipConstant.ADVISOR_EMAIL`） |
| 主题 | `【LoveOfUs】VIP 开通申请 - 昵称 - 档位` |
| 正文 | 用户：昵称，正在开通 VIP 服务；等级：档位（价格 元）；订单号：订单号；开通命令：curl 命令 |

邮件正文示例：

```
用户：小明，正在开通 VIP 服务
等级：月卡（18 元）
订单号：VIP20260912120000123456

开通命令（确认收款后执行）
curl -X POST "https://loveofus.com/api/vip/admin/orders/VIP20260912120000123456/activate" -H "X-Vip-Admin-Token: ******"
```

命令里的域名来自 `VIP_PUBLIC_BASE_URL`，密钥来自 `VIP_ADMIN_TOKEN`，两者都见第四节。

## 三、顾问接口

三个接口都只校验请求头 `X-Vip-Admin-Token`，不需要登录 Token。
下面示例里的 `$VIP_ADMIN_TOKEN` 换成实际的密钥。

### 3.1 查询待开通订单

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/api/vip/admin/orders/pending` |

```bash
curl "https://localhost:8080/api/vip/admin/orders/pending" - H "X-Vip-Admin-Token: $VIP_ADMIN_TOKEN"
```

返回全部待开通订单（`status=0`），按下单时间升序，含下单用户昵称：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "orderNo": "VIP20260912120000123456",
      "userId": 12,
      "nickname": "小明",
      "vipLevel": 2,
      "vipLevelName": "月卡",
      "priceYuan": 18,
      "createdAt": "2026-09-12T12:00:00"
    }
  ],
  "timestamp": 1789000000
}
```

### 3.2 单笔开通

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/api/vip/admin/orders/{orderNo}/activate` |

```bash
curl -X POST "https://你的域名/api/vip/admin/orders/VIP20260912120000123456/activate" \
     -H "X-Vip-Admin-Token: $VIP_ADMIN_TOKEN"
```

开通成功返回该订单，`status=1`，并带上到期时间：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderNo": "VIP20260912120000123456",
    "vipLevel": 2,
    "vipLevelName": "月卡",
    "priceYuan": 18,
    "status": 1,
    "advisorEmail": "xjl20041115@126.com",
    "vipExpireAt": "2026-10-12T12:00:00"
  },
  "timestamp": 1789000000
}
```

### 3.3 批量开通

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/api/vip/admin/orders/batch-activate` |
| 请求体 | `{"orderNos": ["订单号1", "订单号2"]}` |

```bash
curl -X POST "https://你的域名/api/vip/admin/orders/batch-activate" \
     -H "X-Vip-Admin-Token: $VIP_ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"orderNos": ["VIP20260912120000123456", "VIP20260912120000654321"]}'
```

**逐单开通、逐单反馈**：单笔失败不影响其他订单，返回的 `results` 顺序与请求的订单号一致：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 2,
    "successCount": 1,
    "failCount": 1,
    "results": [
      {
        "orderNo": "VIP20260912120000123456",
        "success": true,
        "message": "success",
        "vipLevel": 2,
        "vipExpireAt": "2026-10-12T12:00:00"
      },
      {
        "orderNo": "VIP20260912120000654321",
        "success": false,
        "message": "订单不存在",
        "vipLevel": null,
        "vipExpireAt": null
      }
    ]
  },
  "timestamp": 1789000000
}
```

## 四、密钥与地址配置

| 环境变量 | 对应配置 | 用途 |
| --- | --- | --- |
| `VIP_ADMIN_TOKEN` | `vip.admin-token` | 顾问接口密钥，也是邮件里开通命令携带的密钥 |
| `VIP_PUBLIC_BASE_URL` | `vip.public-base-url` | 生成邮件里开通命令用的对外地址（末尾不带 `/`） |

- 本地 / Compose：在 `.env` 中配置，`docker-compose.yml` 会透传给 backend 容器。
- 未配置 `VIP_ADMIN_TOKEN` 或为空时，顾问接口一律返回 403 `无权限操作`，
  邮件里的命令也会退化成 `<未配置 VIP_ADMIN_TOKEN>`。这是刻意设计，防止接口被越权调用。
- 本地 IDE 直接跑后端时 `.env` 不会被自动加载，需在运行配置里添加这两个环境变量
  （不要写进 `application.yml`，避免密钥入库）。

改动后需重启后端容器：`docker compose up -d backend`。

## 五、档位与时长

| 档位 | 等级 | 价格 | 时长 |
| --- | --- | --- | --- |
| 周卡 | 1 | 6 元 | 7 天 |
| 月卡 | 2 | 18 元 | 30 天 |
| 季卡 | 3 | 48 元 | 90 天 |
| 年卡 | 4 | 168 元 | 365 天 |
| 永久卡 | 5 | 398 元 | 永久 |

档位、价格与权益在 `VipConstant.TIERS` 中集中维护，前后端共用同一份数据。

## 六、到期时间口径

- 以下**下单时间**为基准计算：周卡 +7 天、月卡 +30 天、季卡 +90 天、年卡 +365 天，永久卡无到期时间。
- 用户当前仍在会员期内时，从**原到期时间**顺延，不吞掉剩余天数。
- 组内取等级更高的一方生效，同等级取到期更晚的一方（情侣双人同享）。

## 七、常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 顾问没收到邮件 | 检查 `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD`；后端日志搜「VIP 开通申请邮件发送失败」 |
| 邮件里命令的域名不对 | 检查 `VIP_PUBLIC_BASE_URL` 是否配成公网地址 |
| 403 无权限操作 | `X-Vip-Admin-Token` 与 `VIP_ADMIN_TOKEN` 不一致，或后端未配置该变量 |
| 400 该订单已开通，请勿重复操作 | 订单已开通，勿重复调用 |
| 400 该订单已取消，无法开通 | 用户重复下单导致旧订单作废，请让用户重新下单 |
| 400 该用户已是永久会员，请取消订单并联系用户退款 | 用户已是永久卡，直接退款 |
| 404 订单不存在 | 订单号抄错 |
| 批量开通里同一订单重复出现 | 第一次已开通，第二次返回「该订单已开通」，按失败计入 |

## 八、相关代码

- `LoveMap/src/main/java/com/example/lovemap/controller/VipController.java`
- `LoveMap/src/main/java/com/example/lovemap/service/VipService.java`
- `LoveMap/src/main/java/com/example/lovemap/service/serviceImpl/VipServiceImpl.java`
- `LoveMap/src/main/java/com/example/lovemap/service/AsyncMailService.java`
- `LoveMap/src/main/java/com/example/lovemap/common/constant/VipConstant.java`
- `LoveMap/src/main/resources/mapper/VipOrderMapper.xml`
- `LoveMap/sql/migrate/V17__vip_order.sql`
