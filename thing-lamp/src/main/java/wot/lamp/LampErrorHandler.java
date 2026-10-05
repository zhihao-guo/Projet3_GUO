package wot.lamp;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Explicit JSON errors for malformed request bodies. */
@RestControllerAdvice
public class LampErrorHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedBody(HttpMessageNotReadableException exception) {
        return LampController.error(HttpStatus.BAD_REQUEST, "malformed JSON body");
    }
}
