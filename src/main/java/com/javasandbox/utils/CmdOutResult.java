package com.javasandbox.utils;

import lombok.Data;

@Data
public class CmdOutResult {

    private Integer finished;

    private String stdio;

    private String errorStdio;

    private Long time;

}
