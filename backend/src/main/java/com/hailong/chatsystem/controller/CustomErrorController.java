package com.hailong.chatsystem.controller;

import com.hailong.chatsystem.common.ResponseMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    @ResponseBody
    public ResponseMessage<String> handleError(HttpServletRequest request, HttpServletResponse response) {
        HttpStatus status = getStatus(request);

        return switch (status.value()) {
            case 400 -> ResponseMessage.badRequest("请求参数错误");
            case 401 -> ResponseMessage.unauthorized("未授权访问");
            case 403 -> ResponseMessage.forbidden("禁止访问");
            case 404 -> ResponseMessage.notFound("资源不存在");
            case 405 -> ResponseMessage.error(405, "请求方法不允许");
            case 500 -> ResponseMessage.error("服务器内部错误");
            default -> ResponseMessage.error(status.value(), "未知错误");
        };
    }

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(statusCode);
        } catch (Exception ex) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}