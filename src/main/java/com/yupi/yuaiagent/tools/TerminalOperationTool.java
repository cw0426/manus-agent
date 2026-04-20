package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

/**
 * 终端操作工具
 */
public class TerminalOperationTool {

    private static final int TIMEOUT_SECONDS = 60;

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.redirectErrorStream(false); // 不合并错误流，单独处理
            Process process = builder.start();

            // 使用线程池并行读取标准输出和错误流
            ExecutorService executor = Executors.newFixedThreadPool(2);

            // 读取标准输出
            Future<String> outputFuture = executor.submit(() -> {
                StringBuilder stdout = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                }
                return stdout.toString();
            });

            // 读取错误输出
            Future<String> errorFuture = executor.submit(() -> {
                StringBuilder stderr = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                }
                return stderr.toString();
            });

            // 等待进程完成，设置超时
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                output.append("Command execution timed out after ").append(TIMEOUT_SECONDS).append(" seconds");
            } else {
                // 获取输出结果
                String stdout = outputFuture.get(5, TimeUnit.SECONDS);
                String stderr = errorFuture.get(5, TimeUnit.SECONDS);

                if (!stdout.isEmpty()) {
                    output.append(stdout);
                }
                if (!stderr.isEmpty()) {
                    output.append("[Error] ").append(stderr);
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    output.append("Command execution failed with exit code: ").append(exitCode);
                }
            }

            executor.shutdownNow();

        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }
}
