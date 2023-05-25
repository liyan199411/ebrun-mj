# midjourney-proxy

代理 MidJourney 的discord频道，实现api形式调用AI绘图

## 现有功能
- [x] 支持 Imagine、U、V 指令，绘图完成后回调
- [x] 支持 Describe 指令，根据图片生成 prompt
- [x] 支持中文 prompt 翻译，需配置百度翻译或 gpt
- [x] prompt 敏感词判断，支持覆盖调整
- [x] 任务队列，默认队列10，并发3。可参考 [MidJourney订阅级别](https://docs.midjourney.com/docs/plans) 调整mj.queue

## 后续计划
- [ ] 支持mysql存储，优化任务的查询方式
- [ ] 支持配置账号池，分发绘图任务
- [ ] Imagine 时支持上传图片，作为垫图

## 使用前提
1. 科学上网
2. docker环境
3. 注册 MidJourney，创建自己的频道，参考 https://docs.midjourney.com/docs/quick-start
4. 添加自己的机器人: [流程说明](./docs/discord-bot.md)

## 快速启动

1. 下载镜像
```shell
docker pull novicezk/midjourney-proxy:1.6
```
2. 启动容器，并设置参数
```shell
# /xxx/xxx/config目录下创建 application.yml(配置项)、banned-words.txt(可选，覆盖默认的敏感词文件)
# 参考src/main/resources下的文件
docker run -d --name midjourney-proxy \
 -p 8080:8080 \
 -v /xxx/xxx/config:/home/spring/config \
 --restart=always \
 novicezk/midjourney-proxy:1.6

# 或者直接在启动命令中设置参数
docker run -d --name midjourney-proxy \
 -p 8080:8080 \
 -e mj.discord.guild-id=xxx \
 -e mj.discord.channel-id=xxx \
 -e mj.discord.user-token=xxx \
 -e mj.discord.bot-token=xxx \
 --restart=always \
 novicezk/midjourney-proxy:1.6
```
3. 访问 http://localhost:8080/mj 提示 "项目启动成功"
4. 检查discord频道中新创建的机器人是否在线
5. 调用api接口的根路径为 `http://ip:port/mj`，接口测试地址：`http://ip:port/mj/doc.html`，具体API接口见下文

## 注意事项
1. 启动失败请检查代理问题，尝试设置 mj.proxy.host 和 mj.proxy.port
2. 若回调通知接口失败，请检查网络设置，容器中的宿主机IP通常为172.17.0.1
3. 在 [Issues](https://github.com/novicezk/midjourney-proxy/issues) 中提出其他问题或建议
4. 感兴趣的朋友也欢迎加入交流群讨论一下

 <img src="https://raw.githubusercontent.com/novicezk/midjourney-proxy/main/docs/wechat-qrcode.png" width = "330" height = "350" alt="交流群二维码" align=center />

## 配置项

| 变量名 | 非空 | 描述 |
| :-----| :----: | :---- |
| mj.discord.guild-id | 是 | discord服务器ID |
| mj.discord.channel-id | 是 | discord频道ID |
| mj.discord.user-token | 是 | discord用户Token |
| mj.discord.bot-token | 是 | 自定义机器人Token |
| mj.discord.mj-bot-name | 否 | mj机器人名称，默认 "Midjourney Bot" |
| mj.notify-hook | 否 | 任务状态变更回调地址 |
| mj.task-store.type | 否 | 任务存储方式，默认in_memory(内存\重启后丢失)，可选redis |
| mj.task-store.timeout | 否 | 任务过期时间，过期后删除，默认30天 |
| mj.proxy.host | 否 | 代理host，全局代理不生效时设置 |
| mj.proxy.port | 否 | 代理port，全局代理不生效时设置 |
| mj.queue.core-size | 否 | 并发数，默认为3 |
| mj.queue.queue-size | 否 | 等待队列，默认长度10 |
| mj.queue.timeout-minutes | 否 | 任务超时时间，默认为2分钟 |
| mj.translate-way | 否 | 中文prompt翻译方式，可选null(默认)、baidu、gpt |
| mj.baidu-translate.appid | 否 | 百度翻译的appid |
| mj.baidu-translate.app-secret | 否 | 百度翻译的app-secret |
| mj.openai.gpt-api-key | 否 | gpt的api-key |
| mj.openai.timeout | 否 | openai调用的超时时间，默认30秒 |
| mj.openai.model | 否 | openai的模型，默认gpt-3.5-turbo |
| mj.openai.max-tokens | 否 | 返回结果的最大分词数，默认2048 |
| mj.openai.temperature | 否 | 相似度(0-2.0)，默认0 |
| spring.redis | 否 | 任务存储方式设置为redis，需配置redis相关属性 |

spring.redis配置参考
```yaml
spring:
  redis:
    host: 10.107.xxx.xxx
    port: 6379
    password: xxx
```

## API接口说明

### 1. `http://ip:port/mj/trigger/submit` 提交任务
POST  application/json
```json
{
    // 动作: 必传，IMAGINE（绘图）、UPSCALE（选中放大）、VARIATION（选中变换）
    "action":"IMAGINE",
    // 绘图参数: IMAGINE时必传
    "prompt": "猫猫",
    // 任务ID: UPSCALE、VARIATION时必传
    "taskId": "1320098173412546",
    // 图序号: 1～4，UPSCALE、VARIATION时必传，表示第几张图
    "index": 3,
    // 自定义字符串: 非必传，供回调到业务系统里使用
    "state": "test:22",
    // 支持每个任务配置不同回调地址，非必传
    "notifyHook": "http://localhost:8113/notify"
}
```
返回 `Message` 描述
- code=1: 提交成功，result为任务ID
    ```json
    {
      "code": 1,
      "description": "成功",
      "result": "8498455807619990"
    }
    ```
- code=2: 提交成功，进入队列等待
    ```json
    {
        "code": 2,
        "description": "排队中，前面还有1个任务",
        "result": "0741798445574458"
    }
    ```
- other: 提交错误，description为错误描述

### 2. `http://ip:port/mj/trigger/submit-uv` 提交选中放大或变换任务
POST  application/json
```json
{
    // 自定义参数，非必传
    "state": "test:22",
    // 任务描述: 选中ID为1320098173412546的第2张图片放大
    // 放大 U1～U4 ，变换 V1～V4
    "content": "1320098173412546 U2",
    // 支持每个任务配置不同回调地址，非必传
    "notifyHook": "http://localhost:8113/notify"
}
```
返回结果同 `/trigger/submit`

### 3. `http://ip:port/mj/trigger/describe` 提交describe任务
POST  application/json
```json
{
    // 自定义参数，非必传
    "state": "test:22",
    // 图片的base64字符串
    "base64": "data:image/png;base64,xxx",
    // 支持每个任务配置不同回调地址，非必传
    "notifyHook": "http://localhost:8113/notify"
}
```
返回结果同 `/trigger/submit`

后续任务完成后，task中prompt即为图片生成的prompt
```json
{
  "action":"DESCRIBE",
  "id":"3856553004865376",
  "prompt":"1️⃣ xxx1 --ar 5:4\n\n2️⃣ xxx2 --ar 5:4\n\n3️⃣ xxx3 --ar 5:4\n\n4️⃣ xxx4 --ar 5:4",
  "promptEn":"1️⃣ xxx1 --ar 5:4\n\n2️⃣ xxx2 --ar 5:4\n\n3️⃣ xxx3 --ar 5:4\n\n4️⃣ xxx4 --ar 5:4",
  "description":"/describe 3856553004865376.png",
  "state":"test:22",
  "submitTime":1683779732983,
  "startTime":1683779737321,
  "finishTime":1683779741711,
  "imageUrl":"https://cdn.discordapp.com/ephemeral-attachments/xxxx/xxxx/3856553004865376.png",
  "status":"SUCCESS"
}
```

### 4. `http://ip:port/mj/task/{id}/fetch` GET 查询单个任务
```json
{
    // 动作: IMAGINE（绘图）、UPSCALE（选中放大）、VARIATION（选中变换）
    "action":"IMAGINE",
    // 任务ID
    "id":"8498455807628990",
    // 绘图参数
    "prompt":"猫猫",
    // 翻译后的绘图参数
    "promptEn": "Cat",
    // 执行的命令
    "description":"/imagine 猫猫",
    // 自定义参数
    "state":"test:22",
    // 提交时间
    "submitTime":1682473784826,
    // 开始处理时间
    "startTime":1682473785130,
    // 结束时间
    "finishTime":null,
    // 生成图片的url, 成功时有值
    "imageUrl":"https://cdn.discordapp.com/attachments/xxx/xxx/xxxx_xxxx.png",
    // 生成转存本地图片的url, 成功时有值
    "localImageUrl":"http://54.67.79.231/ebrunimgs/xxx/xxx/xxxx_xxxx.png",
    // 任务状态: NOT_START（未启动）、SUBMITTED（已提交处理）、IN_PROGRESS（执行中）、FAILURE（失败）、SUCCESS（成功）
    "status":"IN_PROGRESS",
    // 失败原因, 失败时有值
    "failReason":""
}
```

### 5. `http://ip:port/mj/task/list` GET 查询所有任务

```json
[
  {
    "action":"IMAGINE",
    "id":"8498455807628990",
    "prompt":"猫猫",
    "promptEn": "Cat",
    "description":"/imagine 猫猫",
    "state":"test:22",
    "submitTime":1682473784826,
    "startTime":1682473785130,
    "finishTime":null,
    "imageUrl":null,
    "status":"IN_PROGRESS",
    "failReason":""
  }
]
```

## `mj.notify-hook` 任务变更回调
POST  application/json
```json
{
    "action":"IMAGINE",
    "id":"8498455807628990",
    "prompt":"猫猫",
    "promptEn": "Cat",
    "description":"/imagine 猫猫",
    "state":"test:22",
    "submitTime":1682473784826,
    "startTime":1682473785130,
    "finishTime":null,
    "imageUrl":null,
    "status":"IN_PROGRESS",
    "failReason":""
}
```

## 应用项目

- [wechat-midjourney](https://github.com/novicezk/wechat-midjourney) : 代理微信客户端，接入MidJourney
