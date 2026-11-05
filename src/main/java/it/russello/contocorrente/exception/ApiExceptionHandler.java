package it.russello.contocorrente.exception;

import it.russello.contocorrente.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(
                        exception.getCode(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_REQUEST",
                "JSON non valido, data non valida o campi non ammessi"
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(
            MethodArgumentTypeMismatchException exception
    ) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_PARAMETER",
                "Il parametro " + exception.getName()
                        + " ha un formato non valido"
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ACCESS_DENIED", "Non ha i permessi per eseguire l'operazione"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalError(Exception exception) {
        log.error("Errore interno durante l'operazione", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_ERROR",
                        "Errore interno durante l'operazione"
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DATA_CONFLICT", "Operazione incompatibile con i vncoli del database"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Object target = exception.getBindingResult().getTarget();
        String code = switch (target) {
            case AccountCreateRequest ignored -> "INVALID_ACCOUNT_DATA";
            case AccountUpdateRequest ignored -> "INVALID_ACCOUNT_DATA";
            case null, default -> "INVALID_REQUEST";
        };
        String message = exception.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(String code, String message) {
    }
}
