package com.kheisark.ldrphotobooth.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "Data yang dikirim tidak valid." : fieldError.getDefaultMessage();
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("INVALID_REQUEST", message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "MISSING_PARAMETER",
                        "Kolom '" + exception.getParameterName() + "' wajib diisi."
                ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("INVALID_PHOTO_COUNT", "Kirim tepat empat foto."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableJson() {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_JSON", "Format data yang dikirim tidak valid."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("PHOTO_TOO_LARGE", "Ukuran satu atau beberapa foto melebihi batas."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMediaType() {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Format permintaan tidak didukung."));
    }
}
