package com.sam.jarstatusportal.Configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Enumeration;


@Component
public class WebSocketFilter implements Filter {


    @Override
    public void doFilter(ServletRequest request, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // Cast the ServletRequest to HttpServletRequest
        HttpServletRequest httpRequest = (HttpServletRequest) request;

//         Log the request URI
//        System.out.println("Incoming Request: " + httpRequest.getRequestURI());

//         Commenting Off for now. This was used so to see the logs of handshake between websocket  ( Don't Delete It )
//         Log all headers (using Enumeration for older Servlet API versions)
//        Enumeration<String> headerNames = httpRequest.getHeaderNames();
//        while (headerNames.hasMoreElements()) {
//            String header = headerNames.nextElement();
//            System.out.println(header + ": " + httpRequest.getHeader(header));
//        }

        // Continue with the filter chain
        filterChain.doFilter(request, servletResponse);
    }
}
