package com.pulse.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Global branded handling for invalid browser routes and unsupported methods. */
@ControllerAdvice
public class RoutingErrorAdvice {

    @ExceptionHandler(NoResourceFoundException.class)
    public String notFound(NoResourceFoundException exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("errorStatus", 404);
        return "pulse-error";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handlerNotFound(NoHandlerFoundException exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("errorStatus", 404);
        return "pulse-error";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String methodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        model.addAttribute("errorStatus", 405);
        return "pulse-error";
    }
}
