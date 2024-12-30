package com.sam.jarstatusportal.Controller;


import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.sam.jarstatusportal.Entity.Domain;
import com.sam.jarstatusportal.Entity.JarWebSocketHandler;
import com.sam.jarstatusportal.Entity.LogWebSocketHandler;
import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Service.GdriveService;
import com.sam.jarstatusportal.Service.GoDaddyService;
import com.sam.jarstatusportal.Service.JarService;
import com.sam.jarstatusportal.Service.ZeroSsLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
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

    @Autowired
    private GoDaddyService goDaddyService;

    private static final String VM_IP = "62.72.42.59";
    private static final String VM_USERNAME = "Administrator";
    private static final String VM_PASSWORD = "Pass1197Pass";

    @Autowired
    private GdriveService gdriveService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ZeroSsLService zeroSSLService;

    @GetMapping("/fetchLogs")
    public List<String> fetchLogs(@RequestParam("sessionId") String sessionId) {

        String redisKey = "logs:" + sessionId;
        List<String> logs = redisTemplate.opsForList().range(redisKey, 0, -1);

        // abeyy dkh ja bhai logs
        System.out.println("Logs retrieved from Redis for sessionId " + sessionId + ":");
        logs.forEach(System.out::println);

        return logs; // Return logs to the client
    }

    @PostMapping("/executeJar")
    public String executeJar(@RequestParam("jarPath") String jarPath, @RequestParam("sessionId") String sessionId,
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


    @GetMapping("/fetchIdWiseDataForJar/{id}")
    public List<User> fetchDataById(@PathVariable(name = "id") Long id) {

        List<User> allDataOfUser = jarService.findAllDataOfUser(id);

        return allDataOfUser;

    }


    //    This is the end point where I start the backup
    @PostMapping("/startBackup")
    public ResponseEntity<String> startBackup(@RequestParam String projectName) {
        String response = gdriveService.startBackup(projectName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/downloadBackup")
    public ResponseEntity<InputStreamResource> downloadBackup(@RequestParam String projectName,
                                                              @RequestParam String fileType) {
        try {
            // Step 1
            String fileId = gdriveService.findLatestFile(projectName, fileType);

            // Step 2
            java.io.File downloadedFile = gdriveService.downloadFile(fileId);

            // Step 3
            InputStreamResource resource = new InputStreamResource(new FileInputStream(downloadedFile));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + downloadedFile.getName())
                    .header(HttpHeaders.CONTENT_TYPE, fileType.equals("zip") ? "application/zip" : "application/sql")
                    .contentLength(downloadedFile.length())
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

//Go Daddy DNS manager started -------------------------
    @GetMapping("/getdomains")
    public ResponseEntity<List<Domain>> getDomains() {
        try {
            List<Domain> domains = goDaddyService.getDomains();
            return ResponseEntity.ok(domains);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }



    @PostMapping("/createSslCertificate")
    public String createCertificate(@RequestParam String domain) {
        return zeroSSLService.createCertificateWithAutoCSR(domain);
    }

    @PostMapping("/verifyDomainByZeroSsl")
    public String verifyDomainByZeroSsl(@RequestParam String domain) {
        return zeroSSLService.verifyDomainByZeroSsl(domain);
    }

}
