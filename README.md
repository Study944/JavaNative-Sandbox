# Java Sandbox with Docker Isolation

一个安全的Java代码执行沙箱，采用Docker隔离技术，用于安全地运行不受信任的代码。

## 特性

- Java代码的安全执行环境
- Docker容器隔离，实现进程分离
- 资源限制（CPU、内存、磁盘）
- 长时间运行代码的超时保护
- 输入/输出重定向和捕获
- [添加您实现的其他特定功能]

## 沙箱实现细节

### 整体架构

本项目采用双层安全架构设计，结合Java安全管理器与Docker容器隔离技术，实现对不受信任代码的安全执行环境。

```
┌─────────────────────────────────────────────────┐
│                  应用层                          │
│  ┌─────────────┐    ┌─────────────────────────┐  │
│  │ CodeSandBox │    │ Docker容器管理         │  │
│  └─────────────┘    └─────────────────────────┘  │
├─────────────────────────────────────────────────┤
│                  隔离层                          │
│  ┌─────────────┐    ┌─────────────────────────┐  │
│  │ Java安全管理器│   │ 资源限制(CPU/内存/磁盘) │  │
│  └─────────────┘    └─────────────────────────┘  │
├─────────────────────────────────────────────────┤
│                  容器层                          │
│  ┌─────────────────────────────────────────┐    │
│  │           Docker容器环境                 │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### Docker隔离实现

项目使用Docker容器技术实现进程级隔离，主要流程包括：

1. **容器创建**: 使用Docker Java API创建隔离容器
   ```java
   DockerClient dockerClient = DockerClientBuilder.getInstance().build();
   CreateContainerResponse containerResponse = dockerClient.createContainerCmd(image)
       .withHostConfig(new HostConfig()
           .withMemory(100 * 1024 * 1024L)  // 内存限制
           .withCpuCount(1)                // CPU核心限制
           .withNetworkMode("none"))       // 网络隔离
       .withAttachStderr(true)
       .withAttachStdout(true)
       .exec();
   ```

2. **代码执行**: 通过`docker exec`在隔离环境中执行代码
   ```java
   ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
       .withCmd("java", "-cp", "/app", "Main")
       .withAttachStderr(true)
       .withAttachStdout(true)
       .exec();
   ```

3. **资源监控**: 实时统计容器资源使用情况
   ```java
   StatsCmd statsCmd = dockerClient.statsCmd(containerId);
   statsCmd.exec(new ResultCallback<Statistics>() {
       @Override
       public void onNext(Statistics statistics) {
           MemoryStatsConfig memoryStats = statistics.getMemoryStats();
           Long maxUsage = memoryStats.getUsage();
           // 记录最大内存使用量
       }
   });
   ```

4. **容器清理**: 执行完成后自动销毁容器，防止资源泄漏

### 安全机制

1. **自定义SecurityManager**

项目实现了`SandboxSecurity`类，严格限制代码的系统资源访问：

```java
public class SandboxSecurity extends SecurityManager {
    @Override
    public void checkRead(String file) {
        throw new SecurityException("文件读取权限被拒绝: " + file);
    }
    
    @Override
    public void checkWrite(String file) {
        throw new SecurityException("文件写入权限被拒绝: " + file);
    }
    
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("命令执行权限被拒绝: " + cmd);
    }
}
```

2. **敏感代码检测**

使用字典树算法检测代码中的敏感词和危险操作：

```java
WordTree wordTree = new WordTree();
wordTree.addWords(Arrays.asList("File", "Write", "Read", "exec"));
String match = wordTree.match(code);
if (match != null) {
    // 拒绝执行包含敏感词的代码
}
```

### 资源限制

1. **时间限制**

通过计时器监控代码执行时间，默认超时时间为5秒：
```java
StopWatch stopWatch = new StopWatch();
stopWatch.start();
// 代码执行
stopWatch.stop();
if (stopWatch.getLastTaskTimeMillis() > 5000) {
    // 超时处理
}
```

2. **内存限制**

双重内存限制机制：
- Docker容器级限制：`--memory=100m`
- JVM级限制：`-Xmx100m`

3. **输出限制**

限制代码输出内容大小，防止恶意输出攻击

### 执行流程

1. **代码保存与编译**
   - 将用户代码保存为临时文件
   - 使用`javac`编译代码
   - 检查编译错误

2. **安全检查**
   - 敏感词检测
   - 代码规范验证

3. **容器化执行**
   - 创建隔离容器
   - 挂载代码目录
   - 执行代码并捕获输出

4. **结果评估**
   - 收集执行时间和内存使用
   - 验证输出结果
   - 返回执行状态和结果

### 错误处理

系统定义了多种执行状态码，精确反馈代码执行情况：

- `ACCEPTED(1)`: 执行成功
- `COMPILE_ERROR(2)`: 编译错误
- `TIME_LIMIT_EXCEEDED(3)`: 时间超限
- `MEMORY_LIMIT_EXCEEDED(4)`: 内存超限
- `RUNTIME_ERROR(5)`: 运行时错误
- `WRONG_ANSWER(6)`: 答案错误

## 开发

### 运行测试

```bash
mvn test
```

### 构建Docker镜像

```bash
docker build -t java-sandbox .
```
## 快速开始

### 前提条件

- Java Development Kit (JDK) 8 或更高版本
- Maven 3.6+
- Docker Engine 19.03+
- Docker Compose（可选，用于高级配置）

### 安装

1. 克隆仓库：
   ```bash
   git clone https://github.com/yourusername/java-sandbox.git
   cd java-sandbox
   ```

2. 构建项目：
   ```bash
   mvn clean package
   ```

3. 构建Docker镜像：
   ```bash
   docker build -t java-sandbox .
   ```

## 使用方法

### 基本用法

```java
// 使用沙箱的示例代码
JavaSandbox sandbox = new JavaSandbox();
SandboxResult result = sandbox.execute("public class Main { public static void main(String[] args) { System.out.println(\"Hello World\"); } }");
System.out.println("输出: " + result.getOutput());
System.out.println("退出码: " + result.getExitCode());
```

### 命令行界面

```bash
# 运行沙箱CLI
java -jar target/java-sandbox.jar --code "public class Main { public static void main(String[] args) { System.out.println(\"Hello World\"); } }"
```

### Docker隔离

沙箱使用Docker容器来隔离代码执行：

```bash
# 内部使用的Docker命令示例
docker run --rm --memory=512m --cpus=0.5 --network=none java-sandbox-executor java -jar executor.jar
```
