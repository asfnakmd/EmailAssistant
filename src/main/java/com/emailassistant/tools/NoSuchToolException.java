package com.emailassistant.tools;

public class NoSuchToolException extends RuntimeException {

    public NoSuchToolException(String toolName) {
        super("No tool found with name: " + toolName);
    }
}