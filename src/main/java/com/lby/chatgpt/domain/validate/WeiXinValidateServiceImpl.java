package com.lby.chatgpt.domain.validate;

import com.lby.chatgpt.application.IWeiXinValidateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author lby
 */
@Service
public class WeiXinValidateServiceImpl implements IWeiXinValidateService {

    @Value("")
    private String token;
    @Override
    public boolean checkSign(String signature, String timestamp, String nonce) {
        return false;
    }
}
