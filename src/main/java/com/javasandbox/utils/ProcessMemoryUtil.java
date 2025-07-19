package com.javasandbox.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class ProcessMemoryUtil {

    public static long getProcessMemoryUsage(Process process) {
        // 获取进程 PID
        String pid = getProcessId(process);
        // 执行 tasklist 命令
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("tasklist | findstr "+ pid);
            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                // 输出格式为 CSV，例如："java.exe","1234",...
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    // 第5个字段是内存使用量（如 " 1,234 K"）
                    String memoryStr = parts[4].replaceAll("\"", "").trim();
                    memoryStr = memoryStr.replaceAll(",", "").split(" ")[0];
                    return Long.parseLong(memoryStr); // 返回内存使用量（单位 KB）
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    // 通过反射获取进程 PID（适用于 Windows）
    private static String getProcessId(Process process) {
        ProcessHandle processHandle = process.toHandle();
        return processHandle.pid() + "";
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec("java -version");
        int i = process.waitFor();
        if (i == 0){
            long processMemoryUsage = getProcessMemoryUsage(process);
            System.out.println(processMemoryUsage);
        }
    }

}
