package com.javasandbox.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.dfa.WordTree;
import com.javasandbox.model.CodeSandBoxDTO;
import com.javasandbox.model.CodeSandBoxResult;
import com.javasandbox.model.JudgeInfo;
import com.javasandbox.security.SandboxSecurity;
import com.javasandbox.service.CodeSandBox;
import com.javasandbox.utils.CmdOutResult;
import com.javasandbox.utils.ProcessMemoryUtil;
import com.javasandbox.utils.ProcessUtil;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Java原生代码沙箱
 */
public class JavaNativeCodeSandBox implements CodeSandBox {

    private static final List<String> BLACK_LIST;

    static {
        BLACK_LIST = Arrays.asList("File","Write","Read");
    }

    public static void main(String[] args) {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        CodeSandBoxDTO codeSandBoxDTO = new CodeSandBoxDTO();
        String code = FileUtil.readString("code/Main.java", StandardCharsets.UTF_8);
//        code = FileUtil.readString("code/TimeError.java", StandardCharsets.UTF_8);
//        code = FileUtil.readString("code/MemoryError.java", StandardCharsets.UTF_8);
//        code = FileUtil.readString("code/FileWriteError.java", StandardCharsets.UTF_8);
//        code = FileUtil.readString("code/FileReadError.java", StandardCharsets.UTF_8);
//        code = FileUtil.readString("code/RunCodeError.java", StandardCharsets.UTF_8);
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
        // 1.读取保存用户代码为Main.java
        // 1.1判断temCode目录是否存在，不存在则创建
        String root = System.getProperty("user.dir");
        String path = root + File.separator + "temCode";
        if (!FileUtil.exist(path)){
            FileUtil.mkdir(path);
        }
        // 1.2保存用户代码为Main.java--temCode/uuid/Main.java
        String filePath =  path + File.separator + UUID.randomUUID();
        String codePath = filePath + File.separator + "Main.java";
        File file = FileUtil.writeString(code, codePath, StandardCharsets.UTF_8);
        // 2.编译代码
        Runtime runtime = Runtime.getRuntime();
        try {
            // 添加字典树解决文件漏洞
            WordTree wordTree = new WordTree();
            wordTree.addWords(BLACK_LIST);
            String match = wordTree.match(code);
            if (match!=null){
                codeSandBoxResult.setMessage("代码中包含敏感词"+ match);
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
            if (finished!=0){
                codeSandBoxResult.setMessage(stdio+errorStdio);
                codeSandBoxResult.setStatus(5);
                return codeSandBoxResult;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 3.运行代码
        // 拼接命令行指令
        List<String> output = new ArrayList<>();
        Long maxTime = 0L;
        for (String s : input) {
            try {
                // Process process = runtime.exec("java -Dfile.encoding=utf-8 -cp " + filePath + " Main " + s);
                // 添加内存限制解决内存漏洞
                 Process process = runtime.exec("java -Dfile.encoding=utf-8 -Xmx56m -cp " + filePath + " Main " + s);
                // 使用自定义Java安全管理器设置文件权限
//                File securityDir = new File("D:\\Java\\idea\\IdeaProjects\\java-sandbox\\src\\main\\resources\\security");
//                Process process = runtime.exec("java -Dfile.encoding=utf-8 " +
//                        "-Djava.security.manager=SandboxSecurity " +
//                        "-cp " + filePath + " ; " + securityDir.getAbsolutePath() + " Main " + s);
                // 添加时间限制解决时间漏洞
                Thread thread = new Thread(() -> {
                    try {
                        Thread.sleep(1000L * 3);
                        if (process.isAlive()) {
                            process.destroy();
                            FileUtil.del(filePath);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                thread.setDaemon(true);
                thread.start();
                // 设置为守护线程
                // TODO 计算内存
                long processMemoryUsage = ProcessMemoryUtil.getProcessMemoryUsage(process);
                // 启动计时器
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                // 等待代码执行结束
                CmdOutResult cmdOutput = ProcessUtil.getCmdOutput(process);
                if (cmdOutput.getErrorStdio().contains("SecurityException")) {
                    System.out.println("SecurityException");
                }
                // 停止计时器，获取时间最大值，因为只要有一个时间超过限制就返回
                stopWatch.stop();
                maxTime = Math.max(stopWatch.getLastTaskTimeMillis(), maxTime);
                Integer finished = cmdOutput.getFinished();
                String stdio = cmdOutput.getStdio();
                String errorStdio = cmdOutput.getErrorStdio();
                // 运行存在错误
                if (finished != 0){
                    codeSandBoxResult.setMessage(errorStdio);
                    codeSandBoxResult.setStatus(5);
                    return codeSandBoxResult;
                }
                output.add(stdio);
            } catch (IOException  e) {
                throw new RuntimeException(e);
            } finally {
                FileUtil.del(filePath);
            }
        }
        codeSandBoxResult.setJudgeInfo(new JudgeInfo( 0L,maxTime));
        if (output.size()==input.size()){
            codeSandBoxResult.setStatus(1);
        }
        codeSandBoxResult.setOutput(output);
        // 4.删除临时文件
        FileUtil.del(filePath);
        // 5.返回结果
        return codeSandBoxResult;
    }

}
