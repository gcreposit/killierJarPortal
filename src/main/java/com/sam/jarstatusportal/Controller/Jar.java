package com.sam.jarstatusportal.Controller;

import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Service.JarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping(path = "/jar")
public class Jar {

    @Autowired
    private JarService jarService;

    @GetMapping(path = "/dashboard")
    public String fetchCleanGangaDetails(Model theModel) {

        List<User> userList = jarService.findAllJarsData();
        theModel.addAttribute("userList", userList);
        return "Home/productionPage";

    }

    @GetMapping(path = "/jarForm")
    public String jarSubmission(Model theModel) {


        return "Home/jarSubmissionForm";

    }


    @PostMapping(path = "/jarSubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String creationJarFile(@ModelAttribute User user) throws IOException {

        String message = jarService.uplaodingAllFormData(user);
        return "redirect:/jar/jarForm";

    }

    @PostMapping(path = "/updateJar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String updationJarFile(@ModelAttribute User user) throws IOException {

        String message = jarService.updationJarFile(user);
        return "redirect:/jar/dashboard";

    }
//Sql/File Backup Page loading function
    @GetMapping(path = "/uploadDownlaodForm")
    public String SqlFileBackupPage(Model theModel) {


        return "Home/SqlFileBackupPage";

    }






}
