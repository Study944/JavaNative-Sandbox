package com.javasandbox.service;


import com.javasandbox.model.CodeSandBoxDTO;
import com.javasandbox.model.CodeSandBoxResult;

/**
 * 代码沙箱接口
 */
public interface CodeSandBox {

    /**
     * 沙箱执行代码请求
     * @param codeSandBoxDTO
     */
    public CodeSandBoxResult runCode(CodeSandBoxDTO codeSandBoxDTO);

}
