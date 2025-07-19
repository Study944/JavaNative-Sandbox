package com.javasandbox.security;

public class SandboxSecurity extends SecurityManager{

    /**
     * 禁止读取文件
     * @param file
     */
    @Override
    public void checkRead(String file) {
        throw new SecurityException("checkRead 权限异常：" + file);
    }

    /**
     * 禁止写入文件
     */
    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite 权限异常：" + file);
    }

    /**
     * 禁止执行系统命令
     * @param cmd
     */
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限异常：" + cmd);
    }

    /**
     * 禁止删除文件
     * @param file
     */
    @Override
    public void checkDelete(String file) {
        throw new SecurityException("checkDelete 权限异常：" + file);
    }

}
