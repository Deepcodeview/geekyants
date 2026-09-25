package com.geekyants.assessment.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, String id) {
        super("%s %s not found".formatted(entity, id));
    }
}
