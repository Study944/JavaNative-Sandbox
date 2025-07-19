package com.javasandbox.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.javasandbox.model.CodeSandBoxDTO;
import com.javasandbox.model.CodeSandBoxResult;
import com.javasandbox.model.JudgeInfo;
import com.javasandbox.model.SubmissionStateEnum;
import com.javasandbox.service.CodeSandBox;
import com.javasandbox.utils.CmdOutResult;
import com.javasandbox.utils.ProcessUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Java原生代码沙箱
 */
@Component
public class JavaDockerCodeSandBox implements CodeSandBox {

    private static final List<String> BLACK_LIST;

    private static final long TIME_OUT = 5000L;

    private static final long MEMORY_OUT = 100L*1024*1024;



    static {
        BLACK_LIST = Arrays.asList("File", "Write", "Read");
    }

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaNativeCodeSandBox = new JavaDockerCodeSandBox();
        CodeSandBoxDTO codeSandBoxDTO = new CodeSandBoxDTO();
        String code = FileUtil.readString("code/Main.java", StandardCharsets.UTF_8);
        codeSandBoxDTO.setCode(code);
        codeSandBoxDTO.setInput(Arrays.asList("1 2", "2 3"));
        codeSandBoxDTO.setLanguage(1);
        CodeSandBoxResult codeSandBoxResult = javaNativeCodeSandBox.runCode(codeSandBoxDTO);
        System.out.println(codeSandBoxResult.toString());
    }


    @Override
    public CodeSandBoxResult runCode(CodeSandBoxDTO codeSandBoxDTO) {
        String code = codeSandBoxDTO.getCode();
        List<String> input = codeSandBoxDTO.getInput();
        CodeSandBoxResult codeSandBoxResult = new CodeSandBoxResult();
        // 1.保存用户代码为Main.java
        // 1.1判断temCode目录是否存在，不存在则创建
        String root = System.getProperty("user.dir");
        String path = root + File.separator + "temCode";
        if (!FileUtil.exist(path)) {
            FileUtil.mkdir(path);
        }
        // 1.2保存用户代码为Main.java--temCode/uuid/Main.java
        String filePath = path + File.separator + UUID.randomUUID();
        String codePath = filePath + File.separator + "Main.java";
        File file = FileUtil.writeString(code, codePath, StandardCharsets.UTF_8);
        // 2.编译代码
        Runtime runtime = Runtime.getRuntime();
        try {
            // 添加字典树解决文件漏洞
            WordTree wordTree = new WordTree();
            wordTree.addWords(BLACK_LIST);
            String match = wordTree.match(code);
            if (match != null) {
                codeSandBoxResult.setMessage("代码中包含敏感词" + match);
                codeSandBoxResult.setStatus(5);
                return codeSandBoxResult;
            }
            Process process = runtime.exec("javac -encoding utf-8 " + file.getAbsolutePath());
            // 等待代码执行结束
            CmdOutResult cmdOutput = ProcessUtil.getCmdOutput(process);
            Integer finished = cmdOutput.getFinished();
            String stdio = cmdOutput.getStdio();
            String errorStdio = cmdOutput.getErrorStdio();
            // 判断编译错误
            if (finished != 0) {
                codeSandBoxResult.setMessage(stdio + errorStdio);
                codeSandBoxResult.setStatus(SubmissionStateEnum.COMPILE_ERROR.getValue());
                return codeSandBoxResult;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 3.运行代码
        // 3.1拉取jdk镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        List<Image> imageList = dockerClient.listImagesCmd().exec();
        String image = "openjdk:11";
        boolean pullJdk = false;
        for (Image i : imageList) {
            if (i.getRepoTags()[0].contains("jdk")) pullJdk = true;
        }
        // jdk镜像不存在，拉取镜像
        if (!pullJdk) {
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    super.onNext(item);
                }
            };
            dockerClient.pullImageCmd(image).exec(pullImageResultCallback);
        }
        // 3.2创建交互式容器运行代码
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        // 设置文件挂载目录
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(filePath, new Volume("/app")));
        CreateContainerResponse containerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = containerResponse.getId();
        // 3.3容器运行字节码文件
        dockerClient.startContainerCmd(containerId).exec();
        CmdOutResult cmdOutResult = new CmdOutResult();
        List<String> output = new ArrayList<>();
        StopWatch stopWatch = new StopWatch();
        Long maxTime = 0L;
        final Long[] maxMemory = {0L};
        for (String i : input) {
            // docker exec containerId java -cp /app Main 1 2
            String[] args = i.split(" ");
            String[] cmd = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, args);
            // 创建命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            // 统计使用内存最大值
            ResultCallback<Statistics> statsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    MemoryStatsConfig memoryStats = statistics.getMemoryStats();
                    Long maxUsage = memoryStats.getUsage();
                    maxMemory[0] = Math.max(maxUsage, maxMemory[0]);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {
                    System.out.println("内存统计结束");
                }
            };
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            statsCmd.exec(statsResultCallback);
            // 执行命令运行用户字节码文件
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    String payLoad = new String(frame.getPayload()).replace("\n","");
                    if (streamType.equals(StreamType.STDERR)) {
                        // 错误信息
                        cmdOutResult.setErrorStdio(payLoad);
                    } else {
                        // 正常结果
                        output.add(payLoad);

                        cmdOutResult.setStdio(payLoad);
                    }
                    super.onNext(frame);
                }
            };
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execCreateCmdResponse.getId())
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("执行失败");
                throw new RuntimeException(e);
            }
            // 计算执行花费的最长时间
            stopWatch.stop();
            maxTime = Math.max(stopWatch.getLastTaskTimeMillis(), maxTime);
        }
        // 整理返回结果
        JudgeInfo judgeInfo = new JudgeInfo(maxMemory[0]/1024,maxTime);
        codeSandBoxResult.setJudgeInfo(judgeInfo);
        codeSandBoxResult.setOutput(output);
        if (maxTime>TIME_OUT){
            codeSandBoxResult.setMessage("超出最大时间限制");
            codeSandBoxResult.setStatus(SubmissionStateEnum.TIME_LIMIT_EXCEEDED.getValue());
            return codeSandBoxResult;
        }
        if (cmdOutResult.getErrorStdio() != null) {
            codeSandBoxResult.setMessage(cmdOutResult.getErrorStdio());
            codeSandBoxResult.setStatus(SubmissionStateEnum.RUNTIME_ERROR.getValue());
            return codeSandBoxResult;
        }
        if (!(output.size() == input.size())) {
            codeSandBoxResult.setMessage("答案错误");
            codeSandBoxResult.setStatus(SubmissionStateEnum.WRONG_ANSWER.getValue());
            return codeSandBoxResult;
        }
        codeSandBoxResult.setStatus(SubmissionStateEnum.ACCEPTED.getValue());
        codeSandBoxResult.setMessage("答案数量正常");
        // 5.删除临时文件，返回结果
        FileUtil.del(filePath);
        return codeSandBoxResult;
    }

}
