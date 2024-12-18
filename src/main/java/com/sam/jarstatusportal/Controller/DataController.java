package com.sam.jarstatusportal.Controller;


import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.sam.jarstatusportal.Entity.JarWebSocketHandler;
import com.sam.jarstatusportal.Entity.LogWebSocketHandler;
import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Service.JarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping(path = "/data")
public class DataController {

    @Autowired
    private JarWebSocketHandler webSocketHandler;

    @Autowired
    private LogWebSocketHandler logWebSocketHandler;

    private static final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    private final Map<String, String> processMap = new HashMap<>();
    @Autowired
    private JarService jarService;

    private static final String VM_IP = "62.72.42.59";
    private static final String VM_USERNAME = "Administrator";
    private static final String VM_PASSWORD = "Pass1197Pass";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/fetchLogs")
    public List<String> fetchLogs(@RequestParam("sessionId") String sessionId) {
        // Fetch logs from Redis
        String redisKey = "logs:" + sessionId;
        List<String> logs = redisTemplate.opsForList().range(redisKey, 0, -1);

        // Print logs retrieved for debugging
        System.out.println("Logs retrieved from Redis for sessionId " + sessionId + ":");
        logs.forEach(System.out::println);

        return logs; // Return logs to the client
    }

    @PostMapping("/executeJar")
    public String executeJar(@RequestParam("jarPath") String jarPath,@RequestParam("sessionId") String sessionId,
                             @RequestParam("id") Long id) {
        final String VM_IP = "62.72.42.59";
        final String VM_USERNAME = "Administrator";
        final String VM_PASSWORD = "Pass1197Pass";
        final String JAR_PATH = "C:\\Users\\Administrator\\Desktop\\JarTesting\\dev3.jar";

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(VM_USERNAME, VM_IP, 22);
            session.setPassword(VM_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String command = String.format("java -jar \"%s\"", jarPath);

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            InputStream inputStream = channelExec.getInputStream();
            InputStream errorStream = channelExec.getErrStream();
            channelExec.connect();

            // Capture STDOUT
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logWebSocketHandler.sendLogToClient(sessionId, "STDOUT: " + line);
                        // Storing  PID dynamically Logic Implemented By SamCr7 using regex
                        if (line.contains("with PID")) {
                            String pid = extractPidFromLine(line);
                            if (pid != null) {
                                jarService.savePidIntoDatabase(pid.trim(), id);
                                System.out.println("Stored PID: " + pid.trim());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Capture STDERR
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logWebSocketHandler.sendLogToClient(sessionId, "STDERR: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            return "JAR execution started. Logs will be streamed.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to start JAR execution. Error: " + e.getMessage();
        }
    }

    /**
     * Utility method to extract PID from log line.
     * Expected format: "with PID <PID>"
     */
    private String extractPidFromLine(String line) {
        Pattern pidPattern = Pattern.compile("with PID (\\d+)");
        Matcher matcher = pidPattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1); // Return the captured PID
        }
        return null;
    }

    @PostMapping("/killJar")
    public String killJar(@RequestParam("id") Long id) {
        // Retrieve the PID stored in the database or activeProcesses map
        String pid = jarService.getPidById(id); // Fetch PID using the provided ID
        if (pid != null) {
            try {
                String killCommand = "taskkill /PID " + pid + " /F"; // Taskkill command for Windows
                executeKillCommandOnVM(killCommand); // Execute command on VM
                System.out.println("Process with PID " + pid + " killed successfully.");

            } catch (Exception e) {
                e.printStackTrace();
                return "Failed to kill the JAR process. Error: " + e.getMessage();
            }
        } else {
            return "No active process found for the given ID.";
        }
        return null;
    }


    private void executeKillCommandOnVM(String command) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(VM_USERNAME, VM_IP, 22);
            session.setPassword(VM_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream inputStream = channel.getInputStream();
            channel.connect();

            // Print command output (optional)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("KILL CMD: " + line);
                }
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            System.err.println("Error executing taskkill command: " + e.getMessage());
            e.printStackTrace();
        }
    }






// DOwn logic is working

//    @PostMapping("/executeJar")
//    public String runJar() {
//        final String VM_IP = "62.72.42.59";
//        final String VM_USERNAME = "Administrator";
//        final String VM_PASSWORD = "Pass1197Pass";
//        final String JAR_PATH = "C:\\Users\\Administrator\\Desktop\\JarTesting\\dev3.jar";
//
//        try {
//            JSch jsch = new JSch();
//            Session session = jsch.getSession(VM_USERNAME, VM_IP, 22);
//            session.setPassword(VM_PASSWORD);
//            session.setConfig("StrictHostKeyChecking", "no");
//            session.connect();
//
//            String command = String.format("java -Dlogging.level.root=INFO -jar \"%s\"", JAR_PATH);
//
//            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
//            channelExec.setCommand(command);
//
//            InputStream inputStream = channelExec.getInputStream();
//            InputStream errorStream = channelExec.getErrStream();
//            channelExec.connect();
//
//            // Capture standard output
//            new Thread(() -> {
//                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        System.out.println("STDOUT: " + line);
//                        webSocketHandler.sendLogToClients(line);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }).start();
//
//            // Capture error output
//            new Thread(() -> {
//                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream))) {
//                    String line;
//                    while ((line = errorReader.readLine()) != null) {
//                        System.out.println("STDERR: " + line);
//                        webSocketHandler.sendLogToClients("ERROR: " + line);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }).start();
//
//            return "Execution started. Logs will be streamed to the browser.";
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "Failed to execute the JAR file. Error: " + e.getMessage();
//        }
//    }
//


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
//    @PostMapping("/killJar")
//    public String killJar(@RequestParam String jarPath) {
//        try {
//            // Format the command to search for the JAR by its filename
//            String command = String.format("for /f \"tokens=1\" %%a in ('jps -l ^| findstr /i \"%s\"') do taskkill /F /PID %%a", jarPath);
//
//            // Use ProcessBuilder to open CMD and execute the command
//            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", command);
//
//            // Inherit the I/O to see the output in real-time
//            processBuilder.inheritIO();
//
//            // Start the process
//            Process process = processBuilder.start();
//
//            // Wait for the process to complete
//            process.waitFor();
//
//            return "Kill command executed successfully.";
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "Error occurred: " + e.getMessage();
//        }
//    }


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
