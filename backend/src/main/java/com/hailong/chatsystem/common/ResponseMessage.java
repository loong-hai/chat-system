package com.hailong.chatsystem.common;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * 统一响应格式
 */
@Setter
@Getter
public class ResponseMessage<T> {

    public ResponseMessage(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public ResponseMessage() {}

    // 成功响应
    public static <T> ResponseMessage<T> success(T data) {
        ResponseMessage<T> responseMessage = new ResponseMessage<>();
        responseMessage.setCode(HttpStatus.OK.value()); // 200
        responseMessage.setMessage("操作成功");
        responseMessage.setData(data);
        return responseMessage;
    }

    public static <T> ResponseMessage<T> success(String message, T data) {
        ResponseMessage<T> responseMessage = new ResponseMessage<>();
        responseMessage.setCode(HttpStatus.OK.value()); // 200
        responseMessage.setMessage(message);
        responseMessage.setData(data);
        return responseMessage;
    }

    public static <T> ResponseMessage<T> success() {
        ResponseMessage<T> responseMessage = new ResponseMessage<>();
        responseMessage.setCode(HttpStatus.OK.value()); // 200
        responseMessage.setMessage("操作成功");
        responseMessage.setData(null);
        return responseMessage;
    }

    public static <T> ResponseMessage<T> success(String message) {
        ResponseMessage<T> responseMessage = new ResponseMessage<>();
        responseMessage.setCode(HttpStatus.OK.value()); // 200
        responseMessage.setMessage(message);
        responseMessage.setData(null);
        return responseMessage;
    }

    // 失败响应
    public static <T> ResponseMessage<T> error(Integer code, String message) {
        return new ResponseMessage<>(code, message, null);
    }

    public static <T> ResponseMessage<T> error(String message) {
        return new ResponseMessage<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), message, null);
    }

    public static <T> ResponseMessage<T> error(Integer code, String message, T data) {
        return new ResponseMessage<>(code, message, data);
    }

    // 未授权
    public static <T> ResponseMessage<T> unauthorized(String message) {
        return new ResponseMessage<>(HttpStatus.UNAUTHORIZED.value(), message, null);
    }

    // 禁止访问
    public static <T> ResponseMessage<T> forbidden(String message) {
        return new ResponseMessage<>(HttpStatus.FORBIDDEN.value(), message, null);
    }

    // 资源未找到
    public static <T> ResponseMessage<T> notFound(String message) {
        return new ResponseMessage<>(HttpStatus.NOT_FOUND.value(), message, null);
    }

    // 请求参数错误
    public static <T> ResponseMessage<T> badRequest(String message) {
        return new ResponseMessage<>(HttpStatus.BAD_REQUEST.value(), message, null);
    }

    // 请求参数错误带数据
    public static <T> ResponseMessage<T> badRequest(String message, T data) {
        return new ResponseMessage<>(HttpStatus.BAD_REQUEST.value(), message, data);
    }

    private Integer code;
    private String message;
    private T data;
}