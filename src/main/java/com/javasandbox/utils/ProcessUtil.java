package com.javasandbox.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessUtil {
    public static CmdOutResult getCmdOutput(Process  process) {
        int waitFor;
        StringBuilder stringBuilder = null;
        StringBuilder errorStringBuilder = null;
        try {
            waitFor = process.waitFor();
            if (waitFor == 0) {
                // 获取标准输出流信息
                stringBuilder = getCommandLindOutput(process);
            } else {
                System.out.println("错误" + waitFor);
                // 获取标准输出流信息
                stringBuilder = getCommandLindOutput(process);
                // 获取错误输出流信息
                errorStringBuilder = getErrorCommandLindOutput(process);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CmdOutResult cmdOutResult = new CmdOutResult();
        cmdOutResult.setFinished(waitFor);
        cmdOutResult.setStdio(stringBuilder.toString());
        if (errorStringBuilder != null) cmdOutResult.setErrorStdio(errorStringBuilder.toString());
        return cmdOutResult;
    }

    private static StringBuilder getErrorCommandLindOutput(Process process) throws IOException {
        BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream(),"GBK"));
        StringBuilder errorStringBuilder = new StringBuilder();
        String errorLine;
        while ((errorLine = errorBufferedReader.readLine()) != null) {
            // 拼接输出结果为一行
            errorStringBuilder.append(errorLine+"\n");
        }
        return errorStringBuilder;
    }

    private static StringBuilder getCommandLindOutput(Process process) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            // 拼接输出结果为一行
            stringBuilder.append(line);
        }
        return stringBuilder;
    }

}
