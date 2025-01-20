package com.sam.jarstatusportal.Controller;


import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.sam.jarstatusportal.Entity.Domain;
import com.sam.jarstatusportal.Entity.JarWebSocketHandler;
import com.sam.jarstatusportal.Entity.LogWebSocketHandler;
import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Service.*;
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

    //------------------------------------------------------------------------------------------------------------------------------------
//@RequestParam("jarFile") MultipartFile jarFile
    // Generate signed URL for the file upload
    @PostMapping("/generateSignedUrl")
    public ResponseEntity<Map<String, String>> generateSignedUrl(
            @RequestParam("developerName") String developerName,
            @RequestParam("website") String website,
            @RequestParam("domain") String domain,
            @RequestParam("port") String port,
            @RequestParam("url") String url,
            @RequestParam("date") String date,
            @RequestParam("month") String month,
            @RequestParam("time") String time,
            @RequestParam("jarFileName") String fileName
    ) throws IOException {

        // Create a user object with the extracted data
        User user = new User();
        user.setDeveloperName(developerName);
        user.setWebsite(website);
        user.setDomain(domain);
        user.setPort(port);
        user.setUrl(url);
        user.setDate(date);
        user.setMonth(month);
        user.setTime(time);
//        user.setJarFile(jarFile);

//        String fileName = jarFile.getOriginalFilename();
//        String fileName = "sameer";

        String signedUrl = jarService.generateSignedUrl(user, fileName);

        Map<String, String> response = new HashMap<>();
        response.put("signedUrl", signedUrl);
//        String customUrl="https://storage.cloud.google.com/gpdm_bucket/uploaded-jars/";
        String customUrl = "uploaded-jars/";
        response.put("filePath", customUrl + fileName); // Include file path
        return ResponseEntity.ok(response);
    }


    // Finalize the upload process
    @PostMapping("/finalizeUpload")
    public ResponseEntity<String> finalizeUpload(@RequestBody User user) throws IOException, JSchException {


        jarService.finalizeUpload(user);
        return ResponseEntity.ok("File uploaded and processed successfully.");
    }

//--------------------------------------------------------------------------------------------------------------------------------------

    @Autowired
    private GdriveService gdriveService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ZeroSsLService zeroSSLService;

    //    Ye logs dkhne ke liye
    @GetMapping("/fetchLogs")
    public List<String> fetchLogs(@RequestParam("sessionId") String sessionId) {

        String redisKey = "logs:" + sessionId;
        List<String> logs = redisTemplate.opsForList().range(redisKey, 0, -1);

        // abeyy dkh ja bhai logs
//        System.out.println("Logs retrieved from Redis for sessionId " + sessionId + ":");
//        logs.forEach(System.out::println);

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
                        logWebSocketHandler.sendLogToClient(sessionId,  line);
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


    @PostMapping("/downloadBackup")
    public ResponseEntity<InputStreamResource> downloadBackup(@RequestParam String projectName,
                                                              @RequestParam String fileType) {
        try {
            // Step 1
            String fileId = gdriveService.findLatestFile(projectName, fileType);

            // Step 2
            java.io.File downloadedFile = gdriveService.downloadFile(fileId);
            String fileName = downloadedFile.getName();
            if (!fileName.endsWith(".zip") && !fileName.endsWith(".sql")) {
                fileName += fileType.equals("zip") ? ".zip" : ".sql";
            }
            // Step 3
            InputStreamResource resource = new InputStreamResource(new FileInputStream(downloadedFile));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
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


    //-----------Generate CSR Mehtod
    @PostMapping("/createSslCertificate")
    public String createCertificate(@RequestParam String domain) {
        return zeroSSLService.createCertificateWithAutoCSR(domain);
    }

    //-----------Verify Domain By Zero SSL
    @PostMapping("/verifyDomainByZeroSsl")
    public String verifyDomainByZeroSsl(@RequestParam String domain) {
        return zeroSSLService.verifyDomainByZeroSsl(domain);
    }

    //Go Daddy DNS manager Ended -------------------------
    @PostMapping("/downlaodZeroSslCertificate")
    public String downlaodZeroSslCertificate(@RequestParam String domain) throws IOException {


        return zeroSSLService.downlaodZeroSslCertificate(domain);
    }

    @Autowired
    private GoogleAuthService googleAuthService;

    @GetMapping("/api/token")
    public String getAccessToken() {
        try {
            return googleAuthService.getAccessToken();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating access token: " + e.getMessage();
        }
    }

    //Saving Session in Redis using persistence Logic
    @PostMapping("/saveSession")
    public ResponseEntity<String> saveSession(@RequestParam String sessionId) {
        redisTemplate.opsForList().rightPush("activeSessions", sessionId); // Add sessionId to Redis

        redisTemplate.opsForHash().put("sessionStatus", sessionId, "UP"); // Set session status to UP
        return ResponseEntity.ok("Session saved successfully.");
    }

    //Getting those  Session from  Redis to load the active session Id
    @GetMapping("/fetchActiveSessions")
    public ResponseEntity<List<String>> fetchActiveSessions() {
        List<String> activeSessions = redisTemplate.opsForList().range("activeSessions", 0, -1);
        return ResponseEntity.ok(activeSessions); // Return all active session IDs
    }

    //Deleting the session from Redis if cross button is clicked (So it deletes the activie session  and it removes the log key)
    @DeleteMapping("/removeSession")
    public ResponseEntity<String> removeSession(@RequestParam String sessionId) {
        redisTemplate.opsForList().remove("activeSessions", 1, sessionId); // Remove sessionId from Redis

        // Delete the logs associated with this session
        String redisKey = "logs:" + sessionId;
        Boolean isLogKeyDeleted = redisTemplate.delete(redisKey);

        String message = "Session " + sessionId + " removed from activeSessions. ";
        message += (isLogKeyDeleted != null && isLogKeyDeleted) ? "Logs deleted successfully." : "No logs found to delete.";
        return ResponseEntity.ok(message);
    }




}
