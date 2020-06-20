package com.gulimall.common.exception;

/**
 * @decription:
 * 错误码定义为5位数字
 * 前两位表示业务场景，后三位表示错误码
 * 10：通用
 * 11：商品
 * 12: 订单
 * 13：购物车
 * 14：物流
 *
 *
 * @author: zyy
 * @date 2020-06-10-18:34
 */
public enum BizCodeEnum {
    UNKNOW_EXCEPTION(10000,"系统位置异常"),
    VALID_EXCEPTION(10001,"参数格式校验失败"),
    SMS_CODE_EXCEPTION(10002,"请求频率太高"),
    SMS_CODE_INVALID_EXCEPTION(10003,"验证码错误"),
    USERNAME_UNIQUE_EXCEPTION(10004,"用户名已存在"),
    PHONE_UNIQUE_EXCEPTION(10005,"手机号已存在"),
    PRODUCT_UP_EXCEPTION(11000,"商品上架异常");

    private int code;
    private String msg;

    BizCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
