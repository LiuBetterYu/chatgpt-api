package com.lby.chatgpt.application;

/**
 * 微信公众号验签服务
 * @author lby
 */
public interface IWeiXinValidateService {

    boolean checkSign(String signature, String timestamp, String nonce);
}
