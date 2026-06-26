package com.example.filebatchprocessor.exception;

import com.example.filebatchprocessor.model.FileAssetStatus;

public class InvalidFileStateTransitionException extends IllegalStateException {

    public InvalidFileStateTransitionException(
            Long fileRecordId, FileAssetStatus from, FileAssetStatus to, String reason) {
        super("Invalid file state transition: fileRecordId=" + fileRecordId
                + ", from=" + from
                + ", to=" + to
                + ", reason=" + reason);
    }
}
