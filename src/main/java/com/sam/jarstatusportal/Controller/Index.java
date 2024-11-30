package com.sam.jarstatusportal.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Index {



    @GetMapping(path = "/")
    public String goToLoginPage(Model theModel) {

        return "redirect:jar/dashboard";
    }

}
