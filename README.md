# Codex Token Monitor

一个面向个人使用的 Android 余额与缓存命中监控工具，配套一个只读取本地 Codex token 统计的小型桥接服务。

作者：**安秀秀（GitHub [@Anxiuxiu1](https://github.com/Anxiuxiu1)）**

> 这是一个个人项目，与 DeepSeek、OpenAI、Codex 官方没有隶属关系。

## 功能

- 查看 DeepSeek API 实时余额、充值余额和赠送余额
- 保留近 7 天余额曲线、今日消耗和日均消耗
- 最近 7 天逐日消耗与当天结束余额
- 显示 Codex 今日缓存命中率、命中 token 和请求次数
- 手机端自动回退地址，并支持局域网广播发现电脑
- API Key 使用 Android Keystore AES-GCM 加密保存
- 支持保存多个 API Key，可为每个 Key 添加备注并切换
- 支持 DeepSeek、Kimi、GPT、Claude、Gemini、GLM、Qwen、Grok 供应商配置
- DeepSeek/Kimi 显示余额；GPT/Claude/Gemini/GLM/Qwen/Grok 使用普通 Key 做连接和模型数量检查
- 供应商和密钥选择使用二次元角色头像卡片
- 本机桥接只读取 token 计数，不读取聊天正文

## 架构

```text
手机 Android App
  ├─ HTTPS → DeepSeek /user/balance
  └─ HTTP  → 本机 cache-bridge.py
                 └─ 读取 Codex 本地 token_usage_record
```

缓存命中字段使用官方 usage 口径对应的 `cached_input_tokens` / `input_tokens` 统计。余额接口只提供余额，不提供历史缓存命中。

## 快速开始

### 1. 运行桥接

电脑需要 Python 3：

```bash
python cache-bridge.py --host 0.0.0.0 --port 8765
```

也可以双击 `start-cache-bridge.cmd`。电脑和手机需要在同一局域网，Windows 防火墙需要允许 TCP 8765 和 UDP 8766 的私有网络访问。

### 2. 打开 App

先用 Android Studio 构建 `app-debug.apk`，再安装到 Android 手机；如果后续发布了构建产物，也可以从 Releases 下载。打开后填入 DeepSeek API Key 并保存。缓存统计源可以保持默认；如果自动发现失败，手动填写：

```text
http://电脑局域网IP:8765/cache?scope=today
```

支持 `scope=today`、`scope=7d` 和 `scope=all`。

### 3. 构建

使用 Android Studio 打开项目，安装 Android SDK 36.1 后执行：

```bash
gradlew.bat :app:assembleDebug
```

## 安全说明

- API Key 不写入明文 `SharedPreferences`，使用 Android Keystore 生成不可导出的 AES 密钥并以 AES-GCM 加密。
- 桥接不读取或上传对话内容，只汇总 `input_tokens`、`cached_input_tokens` 等计数。
- 桥接默认没有鉴权，只建议在可信局域网运行，不要把 8765 端口直接暴露到公网。
- 设备被 Root、存在恶意无障碍服务或被实时读取内存时，任何本地 Key 存储方案都不能提供绝对保证。
- 图标素材由项目发布者提供；如果仓库公开分发，请确认素材版权或替换为自己的图标。

## License

本项目使用 [MIT License](LICENSE)。
