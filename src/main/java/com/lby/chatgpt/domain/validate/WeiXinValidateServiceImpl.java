package com.lby.chatgpt.domain.validate;

import com.lby.chatgpt.application.IWeiXinValidateService;
import com.lby.chatgpt.infrastructure.util.sdk.SignatureUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author lby
 */
@Service
public class WeiXinValidateServiceImpl implements IWeiXinValidateService {

    @Value("${wx.config.token}")
    private String token;
    @Override
    public boolean checkSign(String signature, String timestamp, String nonce) {
        return SignatureUtil.check(token, signature, timestamp, nonce);
    }
}
