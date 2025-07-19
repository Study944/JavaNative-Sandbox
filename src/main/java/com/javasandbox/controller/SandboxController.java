package com.javasandbox.controller;

import com.javasandbox.sandbox.JavaDockerCodeSandBox;
import com.javasandbox.model.CodeSandBoxDTO;
import com.javasandbox.model.CodeSandBoxResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/sandbox")
public class SandboxController {

    @Resource
    JavaDockerCodeSandBox codeSandBox;

    @PostMapping("/run")
    public CodeSandBoxResult runCode(@RequestBody CodeSandBoxDTO codeSandBoxDTO){
        CodeSandBoxResult codeSandBoxResult = codeSandBox.runCode(codeSandBoxDTO);
        return codeSandBoxResult;
    }

}
