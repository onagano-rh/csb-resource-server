package com.redhat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleResource {

    @RequestMapping("/public")
    public Map<String, String> publicArea() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("message", "public area");
        return map;
    }

    @RequestMapping("/protected")
    public Map<String, String> protectedArea() {
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> map = new HashMap<String, String>();
        map.put("message", "protected area");
        map.put("name", token.getName());
        map.put("preferred_username", Objects.toString(token.getTokenAttributes().get("preferred_username")));
        map.put("email", Objects.toString(token.getTokenAttributes().get("email")));
        return map;
    }

    @RequestMapping("/protected/admin")
    public Map<String, String> protectedAreaWithAdminRole() {
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> map = new HashMap<String, String>();
        map.put("message", "protected area with admin role");
        map.put("name", token.getName());
        map.put("preferred_username", Objects.toString(token.getTokenAttributes().get("preferred_username")));
        map.put("email", Objects.toString(token.getTokenAttributes().get("email")));
        return map;
    }

    // For debug
    @RequestMapping("/whoami")
    public Object whoami() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}