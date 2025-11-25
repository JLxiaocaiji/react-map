package org.gistest.common.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "成功标志")
    private boolean success = true;
}
