package com.lang.sekiro.api.databind;

/**
 * param prese解析异常
 */
public class ParamNotPresentException extends RuntimeException {
    private String attributeName;

    public ParamNotPresentException(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }
}
