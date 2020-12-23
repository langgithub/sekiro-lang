## .1. netty-sekiro
## 原文 https://github.com/virjar/sekiro 感谢作者。以下是对原作者内容的学习提取
<!-- TOC -->

- [netty-sekiro](#netty-sekiro)
    - [Gerneral](#gerneral)
        - [sekiro组织架构](#sekiro组织架构)
        - [人观流程](#人观流程)
        - [解决前一版本bug](#解决前一版本bug)

<!-- /TOC -->

### .1.1. Gerneral
SEKIRO 是一个android下的API服务暴露框架，可以用在app逆向、app数据抓取、android群控等场景。
1. sekiro 是用springboot,和netty（nio）开发的高效通讯框架
2. sekiro 不仅支持短连接，也支持长链接，能实时返回手机是否在线的技术
3. sekiro 中使用了netty代表异步请求，服务器资源使用少
4. sekiro 不依赖xposed架构，能与一切hook框架解耦合

#### .1.1.1. sekiro组织架构
![sekiro组织架构](/Sekiro%E6%9E%B6%E6%9E%84.png)

1. sekiro包含移动端开发和服务器端开发
2. 服务器端是用Springboot管理各种组件，在Springboot bean（InitializingBean）初始化 afterPropertiesSet完成会启动两个nio服务，分别监听5600，5601端口.
3. 5601 端口的nio服务器用于处理http请求，即处理调用接口处理。
    * RBHttpRequestDecoder 用于http解码
    * HttpResponseEncoder 用于http编码
    * HttpObjectAggregator 最大报文处理
    * HttpRequestDispatcher 对拿到的http请求处理
4. HttpRequestDispatcher 中接受request请求，封装成SekiroNatMessage 发送给移动端，并且生成一个新任务设置事件回调接口并保存到concurrentmap中.
5. 5600 端口的nio服务器用于处理tcp请求，与移动端数据交互
    * SekiroMessageEncoder tcp编码
    * SekiroMessageDecoder tcp解码
    * ServerIdleCheckHandler 服务器空闲检查 读空闲，写空闲
    * NatServerChannelHandler 自定义处理与移动端数据交互
6. pipeline中的handler是依次执行，符合条件执行
7. SekiroNatMessage 是移动端与服务器交互数据的对象
8. SekiroNatMessage 目前包含3中类型
    * SekiroNatMessage.TYPE_HEARTBEAT 用于处理心跳检查
    * SekiroNatMessage.C_TYPE_REGISTER 用于移动端action注册
    * SekiroNatMessage.TYPE_INVOKE 用于接口调用方，主动hook数据
9. SekiroNatMessage.C_TYPE_REGISTER 实际是将NatClient（封装移动端与服务端的channel，clietid,group信息）保存到natClientMap，poolQueue中
10. SekiroNatMessage.TYPE_INVOKE 从channel中获取clientid，group，换取task任务，调用回调函数。即sekiroResponseEvent.onSekiroResponse(sekiroNatMessage);是将移动端hook后的返回信息（是否注册手机号）传递给回调函数，回调函数会返回response给调用者
11. 移动端即client客户端包含一下handler
    * SekiroMessageDecoder
    * SekiroMessageEncoder
    * ClientIdleCheckHandler 与服务器连接空闲检查
    * ClientChannelHandler 自定义处理服务器端发来的数据
12. ClientChannelHandler将服务器端发来的hook请求取出对于的action 调用
13. sekiroResponse.success  触发channel.writeAndFlush(sekiroNatMessage); 将hook信息发送到服务端

#### .1.1.2. 人观流程
总结：4（调用者的request）->7（发送hook请求注册回调）-> 12（移动端寻找action调用） -> 13（返回hook到的信息） -> 10（服务器端收到hook消息回调给调用者）
1. 其中7，10步骤都是异步，不断发送和接受
2. 如何确保同一任务 这里采用 clientId + "---" + group + "---" + seq; 代表唯一id

#### .1.1.3. 解决前一版本bug
SekiroMessageDecoder 代替原理啊的SekiroNatMessageDecoder（更名为SekiroNatMessageDecoderError）

#### .1.1.4. sekiro 基于xposed主动hook demo
https://github.com/langgithub/hello
#### .1.1.5. sekiroIOS IOS即时通讯部分已完成
https://github.com/langgithub/SekiroIOS
