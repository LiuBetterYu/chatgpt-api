package com.lby.chatgpt.interfaces;

import com.lby.chatgpt.application.IWeiXinValidateService;
import com.lby.chatgpt.common.Constants;
import com.lby.chatgpt.domain.chat.ChatCompletionRequest;
import com.lby.chatgpt.domain.chat.ChatCompletionResponse;
import com.lby.chatgpt.domain.chat.Message;
import com.lby.chatgpt.domain.receive.model.MessageTextEntity;
import com.lby.chatgpt.infrastructure.util.XmlUtil;
import com.lby.chatgpt.session.Configuration;
import com.lby.chatgpt.session.OpenAiSession;
import com.lby.chatgpt.session.OpenAiSessionFactory;
import com.lby.chatgpt.session.defaults.DefaultOpenAiSessionFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lby
 */
@RestController
@RequestMapping("/wx/portal/{appid}")
public class WeiXinPortalController {

    private Logger logger = LoggerFactory.getLogger(WeiXinPortalController.class);

    @Value("${wx.config.originalid}")
    private String originalId;

    @Resource
    private IWeiXinValidateService weiXinValidateService;

    private OpenAiSession openAiSession;

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;

    private Map<String, String> chatGPTMap = new ConcurrentHashMap<>();

    public WeiXinPortalController() {
        // 1. 配置文件
        Configuration configuration = new Configuration();
        configuration.setApiHost("https://api.openai.com/");
        configuration.setApiKey("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");
        configuration.setAuthToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4ZmciLCJleHAiOjE2ODM0MTgwOTYsImlhdCI6MTY4MzQxNDQ5NiwianRpIjoiODIyM2FhZWQtOWJiNS00NjE0LTljNGYtNjNiMTBkYWE1YjA3IiwidXNlcm5hbWUiOiJ4ZmcifQ.5rsy5bOOJl1UG5e4IzSDU7YbUUZ4d_ZXHz2wbk1ne58");
        // 2. 会话工厂
        OpenAiSessionFactory factory = new DefaultOpenAiSessionFactory(configuration);
        // 3. 开启会话
        this.openAiSession = factory.openSession();
        logger.info("开始 openAiSession");
    }

    /**
     * 处理微信服务器发来的get请求，进行签名的验证
     * http://benyugpt.vip.cpolar.cn/wx/portal/wx491678977cf76fed
     * <p>
     * appid     微信端AppID
     * signature 微信端发来的签名
     * timestamp 微信端发来的时间戳
     * nonce     微信端发来的随机字符串
     * echostr   微信端发来的验证字符串
     */
    @GetMapping(produces = "text/plain;charset=utf-8")
    public String validate(@PathVariable String appid,
                           @RequestParam(value = "signature", required = false) String signature,
                           @RequestParam(value = "timestamp", required = false) String timestamp,
                           @RequestParam(value = "nonce", required = false) String nonce,
                           @RequestParam(value = "echostr", required = false) String echostr) {
        try {
            logger.info("微信公众号验签信息{}开始 [{}, {}, {}, {}]", appid, signature, timestamp, nonce, echostr);
            if (StringUtils.isAnyBlank(signature, timestamp, nonce, echostr)) {
                throw new IllegalArgumentException("请求参数非法，请核实!");
            }
            boolean check = weiXinValidateService.checkSign(signature, timestamp, nonce);
            logger.info("微信公众号验签信息{}完成 check：{}", appid, check);
            if (!check) {
                return null;
            }
            return echostr;
        } catch (Exception e) {
            logger.error("微信公众号验签信息{}失败 [{}, {}, {}, {}]", appid, signature, timestamp, nonce, echostr, e);
            return null;
        }
    }

    /**
     * 此处是处理微信服务器的消息转发的
     */
    @PostMapping(produces = "application/xml; charset=UTF-8")
    public String post(@PathVariable String appid,
                       @RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        try {
            logger.info("接收微信公众号信息请求{}开始 {}", openid, requestBody);
            MessageTextEntity message = XmlUtil.xmlToBean(requestBody, MessageTextEntity.class);
            // 异步任务
            if (chatGPTMap.get(message.getContent().trim()) == null || "NULL".equals(chatGPTMap.get(message.getContent().trim()))) {
                // 反馈信息[文本]
                MessageTextEntity res = new MessageTextEntity();
                res.setToUserName(openid);
                res.setFromUserName(originalId);
                res.setCreateTime(String.valueOf(System.currentTimeMillis() / 1000L));
                res.setMsgType("text");
                res.setContent("消息处理中，请再回复我一句【" + message.getContent().trim() + "】");
                if (chatGPTMap.get(message.getContent().trim()) == null) {
                    doChatGPTTask(message.getContent().trim());
                }

                return XmlUtil.beanToXml(res);
            }

            // 反馈信息[文本]
            MessageTextEntity res = new MessageTextEntity();
            res.setToUserName(openid);
            res.setFromUserName(originalId);
            res.setCreateTime(String.valueOf(System.currentTimeMillis() / 1000L));
            res.setMsgType("text");
            res.setContent(chatGPTMap.get(message.getContent().trim()));
            String result = XmlUtil.beanToXml(res);
            logger.info("接收微信公众号信息请求{}完成 {}", openid, result);
            chatGPTMap.remove(message.getContent().trim());
            return result;
        } catch (Exception e) {
            logger.error("接收微信公众号信息请求{}失败 {}", openid, requestBody, e);
            return "";
        }
    }

    public void doChatGPTTask(String content) {
        chatGPTMap.put(content, "NULL");
        taskExecutor.execute(() -> {
            // OpenAI 请求
            // 1. 创建参数
            ChatCompletionRequest chatCompletion = ChatCompletionRequest
                    .builder()
                    .messages(Collections.singletonList(Message.builder().role(Constants.Role.USER).content(content).build()))
                    .model(ChatCompletionRequest.Model.GPT_3_5_TURBO.getCode())
                    .build();
            // 2. 发起请求
            ChatCompletionResponse chatCompletionResponse = openAiSession.completions(chatCompletion);
            // 3. 解析结果
            StringBuilder messages = new StringBuilder();
            chatCompletionResponse.getChoices().forEach(e -> {
                messages.append(e.getMessage().getContent());
            });

            chatGPTMap.put(content, messages.toString());
        });
    }
}
