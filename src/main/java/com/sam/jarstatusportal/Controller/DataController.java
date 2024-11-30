package com.sam.jarstatusportal.Controller;


import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Service.JarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/data")
public class DataController {

    private final Map<String, String> processMap = new HashMap<>();
    @Autowired
    private JarService jarService;

    private static final String VM_IP = "62.72.42.59:33060";
    private static final String VM_USERNAME = "Administrator";
    private static final String VM_PASSWORD = "Pass1197Pass";


    @PostMapping("/executeJar")
    public String executeJar(@RequestParam String jarPath) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(VM_USERNAME, VM_IP.split(":")[0], 22); // Use SSH port 22
            session.setPassword(VM_PASSWORD);

            // Bypass host key verification (not recommended for production)
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();

            // Command to execute the JAR file remotely
            String command = String.format("java -jar \"%s\"", jarPath);

            // Execute command on the remote server
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);
            channelExec.connect();

            // Capture output (optional)
            InputStream inputStream = channelExec.getInputStream();
            String output = new String(inputStream.readAllBytes());

            // Close the channel and session
            channelExec.disconnect();
            session.disconnect();

            return "Execution started on VM: " + output;
        } catch (Exception e) {
            e.printStackTrace();
            return "Execution failed: " + e.getMessage();
        }
    }

//    @PostMapping("/executeJar")
//    public String executeJar(@RequestParam String jarPath) {
//        try {
//            // Command to create a scheduled task that runs the JAR with a visible CMD
//            String createTaskCommand = String.format(
//                    "schtasks /create /tn MyJarTask /tr \"cmd.exe /c java -jar %s\" /sc once /st 00:00 /f /rl highest",
//                    jarPath
//            );
//
//            // Use PsExec to create the scheduled task
//            String createCommand = String.format(
//                    "psexec \\\\%s -u %s -p %s %s",
//                    VM_IP, VM_USERNAME, VM_PASSWORD, createTaskCommand
//            );
//
//            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", createCommand);
//            processBuilder.inheritIO();
//            Process process = processBuilder.start();
//            process.waitFor();
//
//            // Command to run the task immediately
//            String runTaskCommand = String.format(
//                    "psexec \\\\%s -u %s -p %s schtasks /run /tn MyJarTask",
//                    VM_IP, VM_USERNAME, VM_PASSWORD
//            );
//
//            ProcessBuilder runTaskProcessBuilder = new ProcessBuilder("cmd.exe", "/c", runTaskCommand);
//            runTaskProcessBuilder.inheritIO();
//            Process runTaskProcess = runTaskProcessBuilder.start();
//            runTaskProcess.waitFor();
//
//            return "JAR execution started on VM with visible CMD";
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//            return "Execution failed: " + e.getMessage();
//        }
//    }

//    @PostMapping("/executeJar")
//    public String executeJar(@RequestParam String jarPath, @RequestParam String directoryPath) {
//        try {
//            // Command to create a scheduled task that runs the JAR with a visible CMD in the specified path
//            String createTaskCommand = String.format(
//                    "schtasks /create /tn MyJarTask /tr \"cmd.exe /c cd /d %s && java -jar %s\" /sc once /st 00:00 /f /rl highest /v1 /it",
//                    directoryPath, jarPath
//            );
//
//            // Use PsExec to create the scheduled task on the VM
//            String createCommand = String.format(
//                    "psexec \\\\%s -u %s -p %s %s",
//                    VM_IP, VM_USERNAME, VM_PASSWORD, createTaskCommand
//            );
//
//            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", createCommand);
//            processBuilder.inheritIO();
//            Process process = processBuilder.start();
//            process.waitFor();
//
//            // Command to run the task immediately
//            String runTaskCommand = String.format(
//                    "psexec \\\\%s -u %s -p %s schtasks /run /tn MyJarTask",
//                    VM_IP, VM_USERNAME, VM_PASSWORD
//            );
//
//            ProcessBuilder runTaskProcessBuilder = new ProcessBuilder("cmd.exe", "/c", runTaskCommand);
//            runTaskProcessBuilder.inheritIO();
//            Process runTaskProcess = runTaskProcessBuilder.start();
//            runTaskProcess.waitFor();
//
//            return "JAR execution started on VM with visible CMD in the specified path";
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//            return "Execution failed: " + e.getMessage();
//        }
//    }

//------------------------------------It's working method for Normal Localhost----------------------------------------------------------

//@PostMapping("/executeJar")
//public String executeJar(@RequestParam String jarPath) {
//    try {
//        File jarFile = new File(jarPath);
//        String directory = jarFile.getParent();
//
//        // Path to the log file in the same directory as the JAR file
//        String logFilePath = Paths.get(directory, "log.txt").toString();
//        // Command to open a new CMD window and execute the JAR file
//        String command = String.format("cmd.exe /c start cmd.exe /k \"java -jar \"%s\"\"", jarPath);
//
//        // ProcessBuilder to execute the command
//        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", command);
//        processBuilder.inheritIO(); // Inherit I/O for real-time output
//
//        // Start the process
//        Process process = processBuilder.start();
//        process.waitFor(); // Wait for the process to complete
//
//        return "Execution started in a new CMD window";
//    } catch (IOException | InterruptedException e) {
//        e.printStackTrace();
//        return "Execution failed";
//    }
//}
    @PostMapping("/killJar")
    public String killJar(@RequestParam String jarPath) {
        try {
            // Format the command to search for the JAR by its filename
            String command = String.format(
                    "for /f \"tokens=1\" %%a in ('jps -l ^| findstr /i \"%s\"') do taskkill /F /PID %%a",
                    jarPath
            );

            // Use ProcessBuilder to open CMD and execute the command
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", command);

            // Inherit the I/O to see the output in real-time
            processBuilder.inheritIO();

            // Start the process
            Process process = processBuilder.start();

            // Wait for the process to complete
            process.waitFor();

            return "Kill command executed successfully.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error occurred: " + e.getMessage();
        }
    }


    @GetMapping("/fetchIdWiseDataForJar/{id}")
    public List<User> fetchDataById(@PathVariable(name = "id") Long id) {

        List<User> allDataOfUser = jarService.findAllDataOfUser(id);

        return allDataOfUser;

    }


//    @PostMapping("/executeJar")
//    public String executeJar(@RequestParam String jarPath) {
//        try {
//            // Path to your batch file
//            String batchFilePath = "C:\\path\\to\\your\\runJar.bat";
//
//            // Command to open a new CMD window and execute the batch file
//            ProcessBuilder processBuilder = new ProcessBuilder(
//                    "cmd.exe", "/c", "start", "cmd.exe", "/k", batchFilePath + " \"" + jarPath + "\""
//            );
//
//            // Start the process
//            Process process = processBuilder.start();
//            process.waitFor(); // Wait for the process to complete
//
//            return "Execution started in a new CMD window";
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//            return "Execution failed";
//        }
//    }
}
